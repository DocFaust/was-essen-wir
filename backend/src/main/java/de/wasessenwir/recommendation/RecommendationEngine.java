package de.wasessenwir.recommendation;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.random.RandomGenerator;

public final class RecommendationEngine {

    private RecommendationEngine() {}

    public static EngineResult recommend(
            List<Candidate> candidates,
            RatingLookup ratings,
            List<UUID> dinerIds,
            RecommendationPreferences preferences,
            Set<UUID> excludedDishIds,
            RandomGenerator random) {
        return recommend(candidates, ratings, dinerIds, preferences, excludedDishIds, random, RepetitionIndex.empty());
    }

    public static EngineResult recommend(
            List<Candidate> candidates,
            RatingLookup ratings,
            List<UUID> dinerIds,
            RecommendationPreferences preferences,
            Set<UUID> excludedDishIds,
            RandomGenerator random,
            RepetitionIndex repetition) {
        LinkedHashSet<UUID> families = new LinkedHashSet<>();
        List<Candidate> available = new ArrayList<>();
        for (Candidate candidate : candidates) {
            families.add(candidate.dishId());
            if (!excludedDishIds.contains(candidate.dishId())) {
                available.add(candidate);
            }
        }
        List<ScoredCandidate> scored = available.stream()
                .map(candidate -> RecommendationScorer.score(candidate, dinerIds, ratings, preferences, repetition))
                .toList();
        List<ScoredCandidate> picked = SuggestionPicker.pick(scored, random, RecommendationWeights.SUGGESTION_LIMIT);
        List<RankedSuggestion> suggestions = picked.stream()
                .map(candidate -> new RankedSuggestion(candidate, SuggestionReasoner.reason(candidate, preferences)))
                .toList();
        int availableFamilies = (int) available.stream().map(Candidate::dishId).distinct().count();
        return new EngineResult(suggestions, explanation(suggestions.size(), families.size(), availableFamilies));
    }

    static String explanation(int returned, int matchingFamilies, int availableFamilies) {
        if (returned == 0) {
            if (matchingFamilies == 0) {
                return "Für diese Mahlzeit gibt es noch keine passenden Gerichte.";
            }
            return "Die passenden Gerichte sind ausgeschlossen. Hebe einen Ausschluss auf, um wieder Vorschläge zu sehen.";
        }
        if (returned < RecommendationWeights.SUGGESTION_LIMIT) {
            if (returned == 1) {
                return "Für diese Auswahl gibt es nur ein passendes Gericht. Es wird nichts doppelt vorgeschlagen.";
            }
            return "Für diese Auswahl gibt es nur zwei passende Gerichte. Es wird nichts doppelt vorgeschlagen.";
        }
        return null;
    }
}
