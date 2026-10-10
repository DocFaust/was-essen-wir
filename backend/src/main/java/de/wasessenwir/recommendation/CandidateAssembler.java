package de.wasessenwir.recommendation;

import de.wasessenwir.dish.DishInheritance;
import de.wasessenwir.dish.Suitability;
import de.wasessenwir.dish.SuitabilitySetting;
import de.wasessenwir.recommendation.RecommendationRepository.DishRow;
import de.wasessenwir.recommendation.RecommendationRepository.VariantRow;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class CandidateAssembler {

    private CandidateAssembler() {}

    static List<Candidate> assemble(List<DishRow> dishes, List<VariantRow> variants) {
        Map<UUID, List<VariantRow>> byDish = new LinkedHashMap<>();
        for (VariantRow variant : variants) {
            byDish.computeIfAbsent(variant.dishId(), ignored -> new ArrayList<>()).add(variant);
        }
        List<Candidate> candidates = new ArrayList<>();
        for (DishRow dish : dishes) {
            Suitability mealPrep = storedSuitability(dish.mealPrep());
            Suitability homeOffice = storedSuitability(dish.homeOffice());
            candidates.add(new Candidate(dish.id(), dish.name(), null, null, dish.effort(), mealPrep, homeOffice));
            for (VariantRow variant : byDish.getOrDefault(dish.id(), List.of())) {
                candidates.add(new Candidate(
                        dish.id(),
                        dish.name(),
                        variant.id(),
                        variant.name(),
                        DishInheritance.effort(variant.effort(), dish.effort()),
                        DishInheritance.suitability(setting(variant.mealPrep()), mealPrep),
                        DishInheritance.suitability(setting(variant.homeOffice()), homeOffice)));
            }
        }
        return List.copyOf(candidates);
    }

    private static Suitability storedSuitability(Boolean stored) {
        if (stored == null) {
            return Suitability.UNKNOWN;
        }
        return stored ? Suitability.SUITABLE : Suitability.UNSUITABLE;
    }

    private static SuitabilitySetting setting(Boolean stored) {
        if (stored == null) {
            return SuitabilitySetting.INHERIT;
        }
        return stored ? SuitabilitySetting.SUITABLE : SuitabilitySetting.UNSUITABLE;
    }
}
