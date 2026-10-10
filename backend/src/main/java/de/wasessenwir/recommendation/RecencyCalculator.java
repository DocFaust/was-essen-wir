package de.wasessenwir.recommendation;

import de.wasessenwir.meal.MealOccurrence;
import de.wasessenwir.meal.MealStatus;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class RecencyCalculator {

    private RecencyCalculator() {}

    public static RepetitionPenalty penalty(Candidate candidate, List<UUID> dinerIds, RepetitionIndex index) {
        if (dinerIds.isEmpty() || index.occurrences().isEmpty()) {
            return RepetitionPenalty.NONE;
        }
        double eaten = 0;
        double planned = 0;
        for (MealOccurrence event : index.occurrences()) {
            if (!candidate.dishId().equals(event.dishId())) {
                continue;
            }
            double share = overlap(dinerIds, event.participantIds());
            if (share == 0) {
                continue;
            }
            double variantFactor = exact(candidate, event) ? 1 : RecommendationWeights.FAMILY_FACTOR;
            if (event.status() == MealStatus.EATEN) {
                int daysAgo = (int) ChronoUnit.DAYS.between(event.mealDate(), index.referenceDate());
                double base = RecommendationWeights.eatenBase(daysAgo);
                if (base > 0) {
                    eaten += base * variantFactor * share;
                }
            } else if (event.status() == MealStatus.PLANNED) {
                int daysAhead = (int) ChronoUnit.DAYS.between(index.referenceDate(), event.mealDate());
                double base = RecommendationWeights.plannedBase(daysAhead);
                if (base > 0) {
                    planned += base * variantFactor * share;
                }
            }
        }
        return new RepetitionPenalty(
                -Math.min(RecommendationWeights.RECENCY_CAP, eaten),
                -Math.min(RecommendationWeights.PLANNED_CAP, planned));
    }

    static boolean exact(Candidate candidate, MealOccurrence event) {
        if (event.variantId() == null) {
            return candidate.variantId() == null && !event.variantSpecified();
        }
        return event.variantId().equals(candidate.variantId());
    }

    private static double overlap(List<UUID> dinerIds, Set<UUID> participants) {
        int shared = 0;
        for (UUID dinerId : dinerIds) {
            if (participants.contains(dinerId)) {
                shared += 1;
            }
        }
        return (double) shared / dinerIds.size();
    }
}
