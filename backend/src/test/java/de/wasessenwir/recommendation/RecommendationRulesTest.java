package de.wasessenwir.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import de.wasessenwir.dish.Effort;
import de.wasessenwir.dish.Suitability;
import de.wasessenwir.recommendation.RecommendationRepository.DishRow;
import de.wasessenwir.recommendation.RecommendationRepository.VariantRow;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.Test;

class RecommendationRulesTest {

    private final UUID ada = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private final UUID bea = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private final UUID cem = UUID.fromString("00000000-0000-0000-0000-000000000003");

    @Test
    void starWeightsFollowTheScale() {
        assertThat(RecommendationWeights.starWeight(1)).isEqualTo(-8.0);
        assertThat(RecommendationWeights.starWeight(2)).isEqualTo(-3.0);
        assertThat(RecommendationWeights.starWeight(3)).isZero();
        assertThat(RecommendationWeights.starWeight(4)).isEqualTo(3.0);
        assertThat(RecommendationWeights.starWeight(5)).isEqualTo(5.0);
    }

    @Test
    void returnsThreeSuggestionsFromDistinctFamilies() {
        EngineResult result = recommend(
                List.of(candidate("Suppe"), candidate("Kartoffelgratin"), candidate("Spaghetti"), candidate("Gemüsepfanne")),
                List.of(),
                List.of(ada),
                RecommendationPreferences.none(),
                Set.of(),
                new Random(7));

        assertThat(result.suggestions()).hasSize(3);
        assertThat(result.suggestions())
                .extracting(suggestion -> suggestion.scored().candidate().dishId())
                .doesNotHaveDuplicates();
        assertThat(result.explanation()).isNull();
    }

    @Test
    void keepsOnlyOneMemberOfADishFamily() {
        UUID pizza = UUID.randomUUID();
        Candidate base = variant(pizza, "Pizza", null, null);
        Candidate margherita = variant(pizza, "Pizza", UUID.randomUUID(), "Margherita");
        Candidate salami = variant(pizza, "Pizza", UUID.randomUUID(), "Salami");
        EngineResult result = recommend(
                List.of(base, margherita, salami, candidate("Suppe"), candidate("Pfanne")),
                List.of(),
                List.of(ada),
                RecommendationPreferences.none(),
                Set.of(),
                new Random(3));

        assertThat(result.suggestions()).hasSize(3);
        assertThat(result.suggestions())
                .filteredOn(suggestion -> suggestion.scored().candidate().dishId().equals(pizza))
                .hasSize(1);
    }

    @Test
    void canSelectAVariantOfTheFamily() {
        UUID pizza = UUID.randomUUID();
        UUID salami = UUID.randomUUID();
        EngineResult result = recommend(
                List.of(variant(pizza, "Pizza", null, null), variant(pizza, "Pizza", salami, "Salami")),
                List.of(),
                List.of(ada),
                RecommendationPreferences.none(),
                Set.of(),
                new FixedRandom(0.999));

        assertThat(result.suggestions()).hasSize(1);
        assertThat(result.suggestions().getFirst().scored().candidate().variantId()).isEqualTo(salami);
        assertThat(result.suggestions().getFirst().scored().candidate().name()).isEqualTo("Pizza · Salami");
    }

    @Test
    void variantRatingOverridesTheParentAndParentIsOnlyAWeakerSignal() {
        UUID pizza = UUID.randomUUID();
        UUID margherita = UUID.randomUUID();
        Candidate variant = variant(pizza, "Pizza", margherita, "Margherita");

        ScoredCandidate explicit = score(variant, List.of(ada), List.of(
                dishStars(ada, pizza, 1),
                variantStars(ada, margherita, 5)));
        ScoredCandidate inherited = score(variant, List.of(ada), List.of(dishStars(ada, pizza, 5)));
        ScoredCandidate neutralOverride = score(variant, List.of(ada), List.of(
                dishStars(ada, pizza, 5),
                variantStars(ada, margherita, 3)));
        ScoredCandidate inheritedRejection = score(variant, List.of(ada), List.of(dishStars(ada, pizza, 1)));
        ScoredCandidate explicitRejection = score(variant, List.of(ada), List.of(variantStars(ada, margherita, 1)));

        assertThat(explicit.score()).isCloseTo(5, within(1e-9));
        assertThat(inherited.score()).isCloseTo(2.5, within(1e-9));
        assertThat(neutralOverride.score()).isCloseTo(0, within(1e-9));
        assertThat(inheritedRejection.score()).isCloseTo(-4, within(1e-9));
        assertThat(explicitRejection.score()).isCloseTo(-14, within(1e-9));
        assertThat(explicit.score()).isGreaterThan(inherited.score());
        assertThat(inheritedRejection.score()).isGreaterThan(explicitRejection.score());
    }

    @Test
    void oneStarPullsAHighAverageBelowAnUnratedDish() {
        List<UUID> diners = List.of(ada, bea, cem);
        Candidate unfair = candidate("Auflauf");
        Candidate fresh = candidate("Neu");
        Candidate liked = candidate("Liebling");
        ScoredCandidate unfairScore = score(unfair, diners, List.of(
                dishStars(ada, unfair.dishId(), 5),
                dishStars(bea, unfair.dishId(), 5),
                dishStars(cem, unfair.dishId(), 1)));
        ScoredCandidate freshScore = score(fresh, diners, List.of());
        ScoredCandidate likedScore = score(liked, diners, List.of(
                dishStars(ada, liked.dishId(), 4),
                dishStars(bea, liked.dishId(), 4),
                dishStars(cem, liked.dishId(), 4)));

        assertThat(unfairScore.score()).isCloseTo(-4.0 / 3.0, within(1e-9));
        assertThat(freshScore.score()).isCloseTo(1, within(1e-9));
        assertThat(likedScore.score()).isCloseTo(4.5, within(1e-9));
        assertThat(unfairScore.score()).isLessThan(freshScore.score());
        assertThat(freshScore.score()).isLessThan(likedScore.score());
        Candidate explicitNeutral = candidate("Explizit neutral");
        ScoredCandidate neutralScore = score(explicitNeutral, diners, List.of(
                dishStars(ada, explicitNeutral.dishId(), 3),
                dishStars(bea, explicitNeutral.dishId(), 3),
                dishStars(cem, explicitNeutral.dishId(), 3)));
        assertThat(neutralScore.score()).isCloseTo(0, within(1e-9));
        assertThat(freshScore.score()).isNotEqualTo(neutralScore.score());
    }

    @Test
    void missingRatingIsNeutralButNotAnExplicitThree() {
        Candidate dish = candidate("Eintopf");
        ScoredCandidate missing = score(dish, List.of(ada), List.of());
        ScoredCandidate three = score(dish, List.of(ada), List.of(dishStars(ada, dish.dishId(), 3)));

        assertThat(missing.score()).isCloseTo(1, within(1e-9));
        assertThat(three.score()).isCloseTo(0, within(1e-9));
        assertThat(missing.ratings().anySignal()).isFalse();
        assertThat(three.ratings().anySignal()).isTrue();
        assertThat(RecommendationWeights.starWeight(3)).isZero();
    }

    @Test
    void unratedDishesRemainEligible() {
        Candidate fresh = candidate("Unbekannt");
        Candidate loved = candidate("Bekannt");
        EngineResult result = recommend(
                List.of(fresh, loved),
                List.of(dishStars(ada, loved.dishId(), 5)),
                List.of(ada),
                RecommendationPreferences.none(),
                Set.of(),
                new Random(1));

        assertThat(result.suggestions()).hasSize(2);
        assertThat(result.suggestions())
                .filteredOn(suggestion -> suggestion.scored().candidate().dishId().equals(fresh.dishId()))
                .singleElement()
                .extracting(RankedSuggestion::reason)
                .isEqualTo(SuggestionReasoner.UNRATED);
    }

    @Test
    void differentDinersChangeTheRanking() {
        Candidate adaFavorite = candidate("Ada-Gericht");
        Candidate beaFavorite = candidate("Bea-Gericht");
        List<RatingEntry> ratings = List.of(
                dishStars(ada, adaFavorite.dishId(), 5),
                dishStars(ada, beaFavorite.dishId(), 1),
                dishStars(bea, adaFavorite.dishId(), 1),
                dishStars(bea, beaFavorite.dishId(), 5));

        assertThat(score(adaFavorite, List.of(ada), ratings).score())
                .isGreaterThan(score(beaFavorite, List.of(ada), ratings).score());
        assertThat(score(beaFavorite, List.of(bea), ratings).score())
                .isGreaterThan(score(adaFavorite, List.of(bea), ratings).score());
        assertThat(score(adaFavorite, List.of(ada, bea), ratings).score())
                .isCloseTo(score(beaFavorite, List.of(ada, bea), ratings).score(), within(1e-9));
    }

    @Test
    void likedDishesAppearMoreOftenThanRejectedOnes() {
        Candidate loved = candidate("Gerne");
        Candidate hated = candidate("Ungern");
        Candidate other = candidate("Weiteres");
        Candidate another = candidate("Noch eines");
        List<RatingEntry> ratings = new ArrayList<>();
        for (UUID diner : List.of(ada, bea, cem)) {
            ratings.add(dishStars(diner, loved.dishId(), 5));
            ratings.add(dishStars(diner, hated.dishId(), 1));
        }
        int lovedCount = 0;
        int hatedCount = 0;
        for (int seed = 0; seed < 40; seed++) {
            EngineResult result = recommend(
                    List.of(loved, hated, other, another),
                    ratings,
                    List.of(ada, bea, cem),
                    RecommendationPreferences.none(),
                    Set.of(),
                    new Random(seed));
            for (RankedSuggestion suggestion : result.suggestions()) {
                UUID dishId = suggestion.scored().candidate().dishId();
                if (dishId.equals(loved.dishId())) {
                    lovedCount += 1;
                }
                if (dishId.equals(hated.dishId())) {
                    hatedCount += 1;
                }
            }
        }
        assertThat(lovedCount).isGreaterThan(hatedCount);
        assertThat(lovedCount).isGreaterThanOrEqualTo(30);
        assertThat(hatedCount).isLessThanOrEqualTo(8);
    }

    @Test
    void softPreferencesNudgeWithoutDroppingCandidates() {
        Candidate simple = candidate("Apfelpfannkuchen", Effort.EINFACH, Suitability.UNKNOWN, Suitability.UNKNOWN);
        Candidate heavy = candidate("Zwiebelkuchen", Effort.AUFWENDIG, Suitability.UNSUITABLE, Suitability.UNKNOWN);
        RecommendationPreferences preferences = new RecommendationPreferences(true, true, false);

        ScoredCandidate simpleScore = score(simple, List.of(ada), List.of(), preferences);
        ScoredCandidate heavyScore = score(heavy, List.of(ada), List.of(), preferences);
        assertThat(simpleScore.score()).isCloseTo(2.2, within(1e-9));
        assertThat(heavyScore.score()).isCloseTo(0.2, within(1e-9));
        assertThat(heavyScore.selectionWeight()).isGreaterThan(RecommendationWeights.MIN_WEIGHT);

        EngineResult result = recommend(
                List.of(simple, heavy),
                List.of(),
                List.of(ada),
                preferences,
                Set.of(),
                new FixedRandom(0.999));
        assertThat(result.suggestions()).extracting(suggestion -> suggestion.scored().candidate().dishName())
                .contains("Zwiebelkuchen");
        assertThat(result.suggestions()).allSatisfy(suggestion -> assertThat(suggestion.reason()).doesNotContain("Stern"));
    }

    @Test
    void unknownSuitabilityIsNotTreatedAsUnsuitable() {
        Candidate unknown = candidate("Ofen", null, Suitability.UNKNOWN, Suitability.UNKNOWN);
        ScoredCandidate preferred = score(unknown, List.of(ada), List.of(), new RecommendationPreferences(false, true, true));
        ScoredCandidate plain = score(unknown, List.of(ada), List.of(), RecommendationPreferences.none());

        assertThat(preferred.score()).isCloseTo(plain.score(), within(1e-9));
    }

    @Test
    void returnsFewerSuggestionsWhenFewerFamiliesExist() {
        EngineResult two = recommend(
                List.of(candidate("Eins"), candidate("Zwei")),
                List.of(),
                List.of(ada),
                RecommendationPreferences.none(),
                Set.of(),
                new Random(2));
        EngineResult one = recommend(
                List.of(candidate("Nur das")),
                List.of(),
                List.of(ada),
                RecommendationPreferences.none(),
                Set.of(),
                new Random(2));

        assertThat(two.suggestions()).hasSize(2);
        assertThat(two.explanation()).isEqualTo(
                "Für diese Auswahl gibt es nur zwei passende Gerichte. Es wird nichts doppelt vorgeschlagen.");
        assertThat(one.suggestions()).hasSize(1);
        assertThat(one.explanation()).contains("nur ein passendes Gericht");
    }

    @Test
    void explainsWhenNothingMatchesOrEverythingIsExcluded() {
        EngineResult empty = recommend(
                List.of(),
                List.of(),
                List.of(ada),
                RecommendationPreferences.none(),
                Set.of(),
                new Random(1));
        Candidate only = candidate("Einzig");
        EngineResult excluded = recommend(
                List.of(only),
                List.of(),
                List.of(ada),
                RecommendationPreferences.none(),
                Set.of(only.dishId()),
                new Random(1));

        assertThat(empty.suggestions()).isEmpty();
        assertThat(empty.explanation()).isEqualTo("Für diese Mahlzeit gibt es noch keine passenden Gerichte.");
        assertThat(excluded.suggestions()).isEmpty();
        assertThat(excluded.explanation()).contains("ausgeschlossen");
    }

    @Test
    void exclusionRemovesTheWholeFamily() {
        UUID pizza = UUID.randomUUID();
        Candidate base = variant(pizza, "Pizza", null, null);
        Candidate salami = variant(pizza, "Pizza", UUID.randomUUID(), "Salami");
        Candidate soup = candidate("Suppe");
        EngineResult result = recommend(
                List.of(base, salami, soup, candidate("Pfanne")),
                List.of(),
                List.of(ada),
                RecommendationPreferences.none(),
                Set.of(pizza),
                new Random(9));

        assertThat(result.suggestions()).hasSize(2);
        assertThat(result.suggestions())
                .extracting(suggestion -> suggestion.scored().candidate().dishId())
                .doesNotContain(pizza);
    }

    @Test
    void sameSeedRepeatsAndTheRandomSequenceChangesThePick() {
        List<Candidate> dishes = List.of(candidate("Alpha"), candidate("Beta"), candidate("Gamma"));
        EngineResult first = recommend(dishes, List.of(), List.of(ada), RecommendationPreferences.none(), Set.of(), new Random(42));
        EngineResult second = recommend(dishes, List.of(), List.of(ada), RecommendationPreferences.none(), Set.of(), new Random(42));
        EngineResult low = recommend(dishes, List.of(), List.of(ada), RecommendationPreferences.none(), Set.of(), new FixedRandom(0));
        EngineResult high = recommend(dishes, List.of(), List.of(ada), RecommendationPreferences.none(), Set.of(), new FixedRandom(0.999));

        assertThat(names(first)).containsExactlyElementsOf(names(second));
        assertThat(names(low).getFirst()).isNotEqualTo(names(high).getFirst());
    }

    @Test
    void selectionWeightsStayPositiveAndGroupSizesStayComparable() {
        Candidate dish = candidate("Gleich");
        ScoredCandidate alone = score(dish, List.of(ada), List.of(dishStars(ada, dish.dishId(), 5)));
        ScoredCandidate group = score(dish, List.of(ada, bea, cem), List.of(
                dishStars(ada, dish.dishId(), 5),
                dishStars(bea, dish.dishId(), 5),
                dishStars(cem, dish.dishId(), 5)));
        ScoredCandidate rejected = score(dish, List.of(ada, bea, cem), List.of(
                dishStars(ada, dish.dishId(), 1),
                dishStars(bea, dish.dishId(), 1),
                dishStars(cem, dish.dishId(), 1)));

        assertThat(alone.score()).isCloseTo(5, within(1e-9));
        assertThat(group.score()).isCloseTo(6.5, within(1e-9));
        assertThat(group.score()).isLessThan(alone.score() * 3);
        assertThat(rejected.selectionWeight()).isEqualTo(RecommendationWeights.MIN_WEIGHT);
        assertThat(rejected.selectionWeight()).isPositive();
    }

    @Test
    void reasonsStayAggregate() {
        Candidate dish = candidate("Gemeinsam");
        List<UUID> diners = List.of(ada, bea, cem);
        String mixed = score(dish, diners, List.of(
                dishStars(ada, dish.dishId(), 5),
                dishStars(bea, dish.dishId(), 5),
                dishStars(cem, dish.dishId(), 1))).ratings().oneStarCount() == 1
                ? SuggestionReasoner.reason(
                        score(dish, diners, List.of(
                                dishStars(ada, dish.dishId(), 5),
                                dishStars(bea, dish.dishId(), 5),
                                dishStars(cem, dish.dishId(), 1))),
                        RecommendationPreferences.none())
                : "";
        String several = SuggestionReasoner.reason(
                score(dish, diners, List.of(
                        dishStars(ada, dish.dishId(), 4),
                        dishStars(bea, dish.dishId(), 4),
                        dishStars(cem, dish.dishId(), 3))),
                RecommendationPreferences.none());
        String fits = SuggestionReasoner.reason(
                score(dish, List.of(ada), List.of(dishStars(ada, dish.dishId(), 5))),
                RecommendationPreferences.none());

        assertThat(mixed).isEqualTo(SuggestionReasoner.MIXED);
        assertThat(several).isEqualTo(SuggestionReasoner.SEVERAL_LIKE);
        assertThat(fits).isEqualTo(SuggestionReasoner.FITS);
        assertThat(List.of(mixed, several, fits)).allSatisfy(reason -> {
            assertThat(reason).doesNotContain("Stern");
            assertThat(reason).doesNotContain(ada.toString());
            assertThat(reason).doesNotContain(bea.toString());
            assertThat(reason).doesNotContain(cem.toString());
        });
    }

    @Test
    void variantInheritsEffortAndSuitabilityUntilOverridden() {
        UUID dishId = UUID.randomUUID();
        UUID inheritedId = UUID.randomUUID();
        UUID ownId = UUID.randomUUID();
        DishRow dish = new DishRow(dishId, "Pizza", Effort.EINFACH, true, null);
        VariantRow inherited = new VariantRow(inheritedId, dishId, "Margherita", null, null, null);
        VariantRow own = new VariantRow(ownId, dishId, "Salami", Effort.AUFWENDIG, false, false);
        List<Candidate> candidates = CandidateAssembler.assemble(List.of(dish), List.of(inherited, own));

        Candidate margherita = candidates.stream().filter(candidate -> inheritedId.equals(candidate.variantId())).findFirst().orElseThrow();
        Candidate salami = candidates.stream().filter(candidate -> ownId.equals(candidate.variantId())).findFirst().orElseThrow();
        assertThat(margherita.effort()).isEqualTo(Effort.EINFACH);
        assertThat(margherita.mealPrep()).isEqualTo(Suitability.SUITABLE);
        assertThat(margherita.homeOffice()).isEqualTo(Suitability.UNKNOWN);
        assertThat(salami.effort()).isEqualTo(Effort.AUFWENDIG);
        assertThat(salami.mealPrep()).isEqualTo(Suitability.UNSUITABLE);
        assertThat(salami.homeOffice()).isEqualTo(Suitability.UNSUITABLE);
    }

    private static EngineResult recommend(
            List<Candidate> candidates,
            List<RatingEntry> ratings,
            List<UUID> diners,
            RecommendationPreferences preferences,
            Set<UUID> excluded,
            RandomGenerator random) {
        return RecommendationEngine.recommend(
                candidates,
                RatingLookup.from(ratings),
                diners,
                preferences,
                excluded,
                random);
    }

    private ScoredCandidate score(Candidate candidate, List<UUID> diners, List<RatingEntry> ratings) {
        return score(candidate, diners, ratings, RecommendationPreferences.none());
    }

    private ScoredCandidate score(
            Candidate candidate,
            List<UUID> diners,
            List<RatingEntry> ratings,
            RecommendationPreferences preferences) {
        return RecommendationScorer.score(candidate, diners, RatingLookup.from(ratings), preferences);
    }

    private static Candidate candidate(String name) {
        return candidate(name, null, Suitability.UNKNOWN, Suitability.UNKNOWN);
    }

    private static Candidate candidate(String name, Effort effort, Suitability mealPrep, Suitability homeOffice) {
        return new Candidate(UUID.randomUUID(), name, null, null, effort, mealPrep, homeOffice);
    }

    private static Candidate variant(UUID dishId, String dishName, UUID variantId, String variantName) {
        return new Candidate(dishId, dishName, variantId, variantName, null, Suitability.UNKNOWN, Suitability.UNKNOWN);
    }

    private static RatingEntry dishStars(UUID userId, UUID dishId, int stars) {
        return new RatingEntry(userId, dishId, null, stars);
    }

    private static RatingEntry variantStars(UUID userId, UUID variantId, int stars) {
        return new RatingEntry(userId, null, variantId, stars);
    }

    private static List<String> names(EngineResult result) {
        return result.suggestions().stream()
                .map(suggestion -> suggestion.scored().candidate().name())
                .toList();
    }

    private static final class FixedRandom implements RandomGenerator {
        private final double value;

        private FixedRandom(double value) {
            this.value = value;
        }

        @Override
        public double nextDouble() {
            return value;
        }

        @Override
        public long nextLong() {
            return 0;
        }
    }
}
