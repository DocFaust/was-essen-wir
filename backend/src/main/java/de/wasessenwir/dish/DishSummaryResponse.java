package de.wasessenwir.dish;

import java.util.List;
import java.util.UUID;

public record DishSummaryResponse(
        UUID id,
        String name,
        List<MealType> mealTypes,
        Effort effort,
        Suitability mealPrep,
        Suitability homeOffice,
        List<String> variantNames) {
}
