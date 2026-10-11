package de.wasessenwir.recommendation;

import de.wasessenwir.dish.Suitability;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class RecommendationScorer {

    private RecommendationScorer() {}

    public static ScoredCandidate score(
            Candidate candidate,
            List<UUID> dinerIds,
            RatingLookup ratings,
            RecommendationPreferences preferences) {
        return score(candidate, dinerIds, ratings, preferences, RepetitionIndex.empty());
    }

    public static ScoredCandidate score(
            Candidate candidate,
            List<UUID> dinerIds,
            RatingLookup ratings,
            RecommendationPreferences preferences,
            RepetitionIndex repetition) {
        int diners = dinerIds.size();
        double contribution = 0;
        int oneStarCount = 0;
        int positiveCount = 0;
        boolean anySignal = false;
        for (UUID dinerId : dinerIds) {
            Integer own = ownStars(candidate, dinerId, ratings);
            if (own != null) {
                contribution += RecommendationWeights.starWeight(own);
                anySignal = true;
                if (own == 1) {
                    oneStarCount += 1;
                }
                if (own >= 4) {
                    positiveCount += 1;
                }
                continue;
            }
            if (candidate.variantId() != null) {
                Integer parent = ratings.dishStars(dinerId, candidate.dishId());
                if (parent != null) {
                    contribution += RecommendationWeights.INHERITED_FACTOR * RecommendationWeights.starWeight(parent);
                    anySignal = true;
                }
            }
        }

        double preference = contribution / diners;
        double rejection = RecommendationWeights.REJECTION_PER_ONE_STAR * oneStarCount / diners;
        double agreement = positiveCount >= 2 && oneStarCount == 0 ? RecommendationWeights.AGREEMENT : 0;
        double exploration = anySignal ? 0 : RecommendationWeights.EXPLORATION;

        List<ScoreFactor> factors = new ArrayList<>();
        factors.add(new ScoreFactor("preference", preference));
        factors.add(new ScoreFactor("rejection", -rejection));
        factors.add(new ScoreFactor("agreement", agreement));
        factors.add(new ScoreFactor("exploration", exploration));
        factors.addAll(contextFactors(candidate, preferences));
        RepetitionPenalty repetitionPenalty = RecencyCalculator.penalty(candidate, dinerIds, repetition);
        factors.add(new ScoreFactor("recency", repetitionPenalty.eaten()));
        factors.add(new ScoreFactor("planned", repetitionPenalty.planned()));

        double score = 0;
        for (ScoreFactor factor : factors) {
            score += factor.value();
        }
        double weight = Math.max(RecommendationWeights.MIN_WEIGHT, score + RecommendationWeights.WEIGHT_OFFSET);
        return new ScoredCandidate(
                candidate,
                score,
                weight,
                List.copyOf(factors),
                new RatingSummary(diners, preference, oneStarCount, positiveCount, anySignal));
    }

    private static Integer ownStars(Candidate candidate, UUID dinerId, RatingLookup ratings) {
        if (candidate.variantId() == null) {
            return ratings.dishStars(dinerId, candidate.dishId());
        }
        return ratings.variantStars(dinerId, candidate.variantId());
    }

    private static List<ScoreFactor> contextFactors(Candidate candidate, RecommendationPreferences preferences) {
        List<ScoreFactor> factors = new ArrayList<>();
        if (preferences.preferSimple() && candidate.effort() != null) {
            double effort = switch (candidate.effort()) {
                case EINFACH -> RecommendationWeights.SIMPLE_EFFORT;
                case MITTEL -> RecommendationWeights.MEDIUM_EFFORT;
                case AUFWENDIG -> RecommendationWeights.HIGH_EFFORT;
            };
            factors.add(new ScoreFactor("effort", effort));
        }
        if (preferences.preferHomeOffice()) {
            factors.add(new ScoreFactor("homeoffice", suitabilityWeight(candidate.homeOffice())));
        }
        if (preferences.preferMealPrep()) {
            factors.add(new ScoreFactor("mealPrep", suitabilityWeight(candidate.mealPrep())));
        }
        return factors;
    }

    private static double suitabilityWeight(Suitability suitability) {
        return switch (suitability) {
            case SUITABLE -> RecommendationWeights.SUITABLE;
            case UNSUITABLE -> RecommendationWeights.UNSUITABLE;
            case UNKNOWN -> 0;
        };
    }

    public static double factor(ScoredCandidate scored, String id) {
        return scored.factors().stream()
                .filter(factor -> factor.id().equals(id))
                .mapToDouble(ScoreFactor::value)
                .findFirst()
                .orElse(0);
    }
}
