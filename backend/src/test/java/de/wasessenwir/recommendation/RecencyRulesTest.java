package de.wasessenwir.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import de.wasessenwir.dish.Suitability;
import de.wasessenwir.meal.MealOccurrence;
import de.wasessenwir.meal.MealStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.Test;

class RecencyRulesTest {

    private final LocalDate reference = LocalDate.of(2026, 10, 10);
    private final UUID ada = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private final UUID bea = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private final UUID pizza = UUID.fromString("00000000-0000-0000-0000-000000000010");
    private final UUID margherita = UUID.fromString("00000000-0000-0000-0000-000000000011");
    private final UUID salami = UUID.fromString("00000000-0000-0000-0000-000000000012");

    @Test
    void todayAndYesterdayReduceTheChanceAndTheWindowEnds() {
        Candidate dish = base("Pizza");

        assertThat(eatenPenalty(dish, List.of(ada), List.of(eaten(dish, reference, ada))))
                .isEqualTo(-8);
        assertThat(eatenPenalty(dish, List.of(ada), List.of(eaten(dish, reference.minusDays(1), ada))))
                .isEqualTo(-6);
        assertThat(eatenPenalty(dish, List.of(ada), List.of(eaten(dish, reference.minusDays(3), ada))))
                .isEqualTo(-4);
        assertThat(eatenPenalty(dish, List.of(ada), List.of(eaten(dish, reference.minusDays(7), ada))))
                .isEqualTo(-2);
        assertThat(eatenPenalty(dish, List.of(ada), List.of(eaten(dish, reference.minusDays(14), ada))))
                .isEqualTo(-1);
        assertThat(eatenPenalty(dish, List.of(ada), List.of(eaten(dish, reference.minusDays(15), ada))))
                .isZero();
        assertThat(eatenPenalty(dish, List.of(ada), List.of(eaten(dish, reference.plusDays(1), ada))))
                .isZero();
    }

    @Test
    void repeatedMealsAddPenaltyUntilTheCap() {
        Candidate dish = base("Pizza");
        double once = eatenPenalty(dish, List.of(ada), List.of(eaten(dish, reference.minusDays(1), ada)));
        double twice = eatenPenalty(dish, List.of(ada), List.of(
                eaten(dish, reference.minusDays(1), ada),
                eaten(dish, reference.minusDays(5), ada)));
        double many = eatenPenalty(dish, List.of(ada), List.of(
                eaten(dish, reference, ada),
                eaten(dish, reference.minusDays(1), ada),
                eaten(dish, reference.minusDays(2), ada),
                eaten(dish, reference.minusDays(3), ada)));

        assertThat(once).isEqualTo(-6);
        assertThat(twice).isEqualTo(-8);
        assertThat(twice).isLessThan(once);
        assertThat(many).isEqualTo(-RecommendationWeights.RECENCY_CAP);
    }

    @Test
    void anotherVariantOfTheFamilyIsPenalizedOnceAndLess() {
        Candidate margheritaCandidate = variant(margherita, "Margherita");
        Candidate salamiCandidate = variant(salami, "Salami");
        Candidate baseDish = pizzaDish();
        MealOccurrence eatenMargherita = occurrence(pizza, margherita, false, reference.minusDays(1), MealStatus.EATEN, ada);

        double same = eatenPenalty(margheritaCandidate, List.of(ada), List.of(eatenMargherita));
        double sibling = eatenPenalty(salamiCandidate, List.of(ada), List.of(eatenMargherita));
        double parent = eatenPenalty(baseDish, List.of(ada), List.of(eatenMargherita));

        assertThat(same).isEqualTo(-6);
        assertThat(sibling).isEqualTo(-3);
        assertThat(parent).isEqualTo(-3);
        assertThat(sibling).isGreaterThan(same);
    }

    @Test
    void aDeletedVariantDoesNotCountAsTheBaseDish() {
        Candidate baseDish = pizzaDish();
        MealOccurrence removedVariant = new MealOccurrence(
                pizza, null, true, reference.minusDays(1), MealStatus.EATEN, Set.of(ada));

        assertThat(eatenPenalty(baseDish, List.of(ada), List.of(removedVariant))).isEqualTo(-3);
        assertThat(RecencyCalculator.exact(baseDish, removedVariant)).isFalse();
    }

    @Test
    void onlyTheDinersWhoWereThereShareThePenalty() {
        Candidate dish = base("Pizza");
        MealOccurrence wernerAte = eaten(dish, reference.minusDays(1), ada);

        assertThat(eatenPenalty(dish, List.of(ada, bea), List.of(wernerAte))).isEqualTo(-3);
        assertThat(eatenPenalty(dish, List.of(ada), List.of(wernerAte))).isEqualTo(-6);
        assertThat(eatenPenalty(dish, List.of(bea), List.of(wernerAte))).isZero();
    }

    @Test
    void plannedMealsPenalizeSeparatelyFromHistory() {
        Candidate dish = pizzaDish();
        MealOccurrence tomorrow = occurrence(pizza, null, false, reference.plusDays(1), MealStatus.PLANNED, ada);
        MealOccurrence nextWeek = occurrence(pizza, null, false, reference.plusDays(7), MealStatus.PLANNED, ada);
        MealOccurrence missed = occurrence(pizza, null, false, reference.minusDays(1), MealStatus.PLANNED, ada);

        ScoredCandidate planned = score(dish, List.of(tomorrow, nextWeek));
        assertThat(RecommendationScorer.factor(planned, "planned")).isEqualTo(-4);
        assertThat(RecommendationScorer.factor(planned, "recency")).isZero();
        assertThat(RecommendationScorer.factor(score(dish, List.of(missed)), "planned")).isZero();
        assertThat(SuggestionReasoner.reason(planned, RecommendationPreferences.none()))
                .isEqualTo(SuggestionReasoner.PLANNED_SOON);
    }

    @Test
    void aConfirmedMealIsNotPenalizedAsPlanAndHistory() {
        Candidate dish = base("Pizza");
        MealOccurrence confirmed = eaten(dish, reference, ada);

        ScoredCandidate scored = score(dish, List.of(confirmed));

        assertThat(RecommendationScorer.factor(scored, "recency")).isEqualTo(-8);
        assertThat(RecommendationScorer.factor(scored, "planned")).isZero();
        assertThat(scored.selectionWeight()).isGreaterThanOrEqualTo(RecommendationWeights.MIN_WEIGHT);
        assertThat(SuggestionReasoner.reason(scored, RecommendationPreferences.none()))
                .isEqualTo(SuggestionReasoner.RECENT);
    }

    @Test
    void repetitionStaysPossibleBecauseTheMinimumWeightRemains() {
        Candidate dish = pizzaDish();
        List<MealOccurrence> heavy = List.of(
                eaten(dish, reference, ada),
                eaten(dish, reference.minusDays(1), ada),
                eaten(dish, reference.minusDays(2), ada),
                occurrence(pizza, null, false, reference, MealStatus.PLANNED, ada),
                occurrence(pizza, null, false, reference.plusDays(1), MealStatus.PLANNED, ada));

        ScoredCandidate scored = score(dish, heavy);

        assertThat(scored.selectionWeight()).isEqualTo(RecommendationWeights.MIN_WEIGHT);
        assertThat(scored.selectionWeight()).isPositive();
    }

    @Test
    void theSameReferenceDateAndSeedRepeatTheSuggestions() {
        Candidate fresh = base("Suppe");
        Candidate recent = base("Pizza");
        RepetitionIndex index = new RepetitionIndex(reference, List.of(eaten(recent, reference, ada)));
        RandomGenerator firstRandom = new java.util.Random(42);
        RandomGenerator secondRandom = new java.util.Random(42);

        EngineResult first = RecommendationEngine.recommend(
                List.of(fresh, recent),
                RatingLookup.empty(),
                List.of(ada),
                RecommendationPreferences.none(),
                Set.of(),
                firstRandom,
                index);
        EngineResult second = RecommendationEngine.recommend(
                List.of(fresh, recent),
                RatingLookup.empty(),
                List.of(ada),
                RecommendationPreferences.none(),
                Set.of(),
                secondRandom,
                index);

        assertThat(names(first)).isEqualTo(names(second));
        assertThat(score(recent, index.occurrences()).score())
                .isLessThan(score(fresh, List.of()).score());
        assertThat(score(recent, List.of(eaten(recent, reference.plusDays(40), ada))).score())
                .isCloseTo(score(fresh, List.of()).score(), within(1e-9));
    }

    @Test
    void anUnratedDishWithoutMealsKeepsTheExplorationReason() {
        ScoredCandidate scored = score(base("Neu"), List.of());

        assertThat(RecommendationScorer.factor(scored, "recency")).isZero();
        assertThat(RecommendationScorer.factor(scored, "planned")).isZero();
        assertThat(SuggestionReasoner.reason(scored, RecommendationPreferences.none()))
                .isEqualTo(SuggestionReasoner.UNRATED);
    }

    @Test
    void theInstallationDateIsNotTheUtcDateAroundMidnight() {
        Instant instant = Instant.parse("2026-10-10T23:30:00Z");

        assertThat(LocalDate.now(Clock.fixed(instant, ZoneOffset.UTC))).isEqualTo(LocalDate.of(2026, 10, 10));
        assertThat(LocalDate.now(Clock.fixed(instant, ZoneId.of("Europe/Berlin"))))
                .isEqualTo(LocalDate.of(2026, 10, 11));
    }

    private double eatenPenalty(Candidate candidate, List<UUID> diners, List<MealOccurrence> events) {
        return RecommendationScorer.factor(score(candidate, diners, events), "recency");
    }

    private ScoredCandidate score(Candidate candidate, List<MealOccurrence> events) {
        return score(candidate, List.of(ada), events);
    }

    private ScoredCandidate score(Candidate candidate, List<UUID> diners, List<MealOccurrence> events) {
        return RecommendationScorer.score(
                candidate,
                diners,
                RatingLookup.empty(),
                RecommendationPreferences.none(),
                new RepetitionIndex(reference, events));
    }

    private MealOccurrence eaten(Candidate candidate, LocalDate date, UUID... diners) {
        return occurrence(candidate.dishId(), candidate.variantId(), candidate.variantId() != null, date, MealStatus.EATEN, diners);
    }

    private static MealOccurrence occurrence(
            UUID dishId,
            UUID variantId,
            boolean variantSpecified,
            LocalDate date,
            MealStatus status,
            UUID... diners) {
        return new MealOccurrence(dishId, variantId, variantSpecified, date, status, Set.of(diners));
    }

    private Candidate base(String name) {
        return new Candidate(UUID.randomUUID(), name, null, null, null, Suitability.UNKNOWN, Suitability.UNKNOWN);
    }

    private Candidate pizzaDish() {
        return new Candidate(pizza, "Pizza", null, null, null, Suitability.UNKNOWN, Suitability.UNKNOWN);
    }

    private Candidate variant(UUID variantId, String name) {
        return new Candidate(pizza, "Pizza", variantId, name, null, Suitability.UNKNOWN, Suitability.UNKNOWN);
    }

    private static List<String> names(EngineResult result) {
        return result.suggestions().stream()
                .map(suggestion -> suggestion.scored().candidate().name())
                .toList();
    }
}
