package de.wasessenwir.recommendation;

import de.wasessenwir.dish.InvalidRequestException;
import de.wasessenwir.meal.MealEventRepository;
import de.wasessenwir.meal.MealOccurrence;
import de.wasessenwir.security.CurrentUser;
import de.wasessenwir.user.UserRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.random.RandomGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RecommendationService {

    private static final int MAX_DINERS = 24;
    private static final int MAX_EXCLUSIONS = 100;

    private final RecommendationRepository repository;
    private final MealEventRepository meals;
    private final UserRepository users;
    private final Clock clock;

    public RecommendationService(
            RecommendationRepository repository,
            MealEventRepository meals,
            UserRepository users,
            Clock clock) {
        this.repository = repository;
        this.meals = meals;
        this.users = users;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public RecommendationResponse recommend(RecommendationRequest request) {
        return recommend(request, CatalogSelection.all());
    }

    @Transactional(readOnly = true)
    public RecommendationResponse recommend(RecommendationRequest request, CatalogSelection selection) {
        CurrentUser.requireId();
        if (request == null || request.mealType() == null) {
            throw new InvalidRequestException("Bitte wähle eine Mahlzeit.");
        }
        CatalogSelection catalog = selection == null ? CatalogSelection.all() : selection;
        List<UUID> dinerIds = diners(request.dinerIds());
        Set<UUID> excluded = excluded(request.excludedDishIds());
        RecommendationPreferences preferences = new RecommendationPreferences(
                Boolean.TRUE.equals(request.preferSimple()),
                Boolean.TRUE.equals(request.preferHomeOffice()),
                Boolean.TRUE.equals(request.preferMealPrep()));
        LocalDate referenceDate = referenceDate(request.referenceDate());
        RandomGenerator random = request.seed() == null ? new Random() : new Random(request.seed());
        List<MealOccurrence> occurrences = meals.findOccurrences(referenceDate);
        RepetitionIndex repetition = new RepetitionIndex(referenceDate, occurrences);

        List<RecommendationRepository.DishRow> dishes = repository.findDishes(request.mealType().name());
        List<Candidate> candidates;
        RatingLookup ratings;
        if (dishes.isEmpty()) {
            candidates = List.of();
            ratings = RatingLookup.empty();
        } else {
            List<UUID> dishIds = dishes.stream().map(RecommendationRepository.DishRow::id).toList();
            List<RecommendationRepository.VariantRow> variants = repository.findVariants(dishIds);
            candidates = CandidateAssembler.assemble(dishes, variants);
            if (catalog.restricted()) {
                candidates = candidates.stream().filter(catalog::includes).toList();
                if (candidates.isEmpty()) {
                    return new RecommendationResponse(
                            List.of(),
                            "In der Sammlung gibt es dazu für diese Mahlzeit kein passendes Gericht.");
                }
            }
            List<UUID> ratedDishIds = candidates.stream().map(Candidate::dishId).distinct().toList();
            List<UUID> variantIds = candidates.stream()
                    .map(Candidate::variantId)
                    .filter(Objects::nonNull)
                    .toList();
            ratings = RatingLookup.from(repository.findRatings(dinerIds, ratedDishIds, variantIds));
        }
        EngineResult result = RecommendationEngine.recommend(
                candidates, ratings, dinerIds, preferences, excluded, random, repetition);
        return new RecommendationResponse(
                result.suggestions().stream().map(RecommendationService::toResponse).toList(),
                result.explanation());
    }

    private List<UUID> diners(List<UUID> dinerIds) {
        if (dinerIds == null || dinerIds.isEmpty()) {
            throw new InvalidRequestException("Mindestens eine Person muss ausgewählt sein.");
        }
        if (dinerIds.size() > MAX_DINERS) {
            throw new InvalidRequestException("Es können höchstens 24 Mitessende ausgewählt werden.");
        }
        LinkedHashSet<UUID> unique = new LinkedHashSet<>();
        for (UUID dinerId : dinerIds) {
            if (dinerId == null) {
                throw new InvalidRequestException("Die Mitessenden sind ungültig.");
            }
            unique.add(dinerId);
        }
        if (users.findActiveIds(unique).size() != unique.size()) {
            throw new InvalidRequestException("Nur aktive Familienmitglieder können mitessen.");
        }
        return List.copyOf(unique);
    }

    private LocalDate referenceDate(LocalDate requested) {
        if (requested == null) {
            return LocalDate.now(clock);
        }
        if (requested.isBefore(LocalDate.of(2000, 1, 1)) || requested.isAfter(LocalDate.of(2100, 12, 31))) {
            throw new InvalidRequestException("Das Referenzdatum liegt außerhalb des zulässigen Bereichs.");
        }
        return requested;
    }

    private static Set<UUID> excluded(List<UUID> dishIds) {
        if (dishIds == null || dishIds.isEmpty()) {
            return Set.of();
        }
        if (dishIds.size() > MAX_EXCLUSIONS) {
            throw new InvalidRequestException("Es können höchstens 100 Gerichte ausgeschlossen werden.");
        }
        LinkedHashSet<UUID> unique = new LinkedHashSet<>();
        for (UUID dishId : dishIds) {
            if (dishId == null) {
                throw new InvalidRequestException("Ein ausgeschlossenes Gericht ist ungültig.");
            }
            unique.add(dishId);
        }
        return Set.copyOf(unique);
    }

    private static SuggestionResponse toResponse(RankedSuggestion suggestion) {
        Candidate candidate = suggestion.scored().candidate();
        return new SuggestionResponse(
                candidate.dishId(),
                candidate.variantId(),
                candidate.name(),
                candidate.dishName(),
                candidate.variantName(),
                suggestion.reason(),
                candidate.effort(),
                candidate.mealPrep(),
                candidate.homeOffice());
    }
}
