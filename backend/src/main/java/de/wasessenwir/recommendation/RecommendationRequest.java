package de.wasessenwir.recommendation;

import de.wasessenwir.dish.MealType;
import java.util.List;
import java.util.UUID;

public record RecommendationRequest(
        MealType mealType,
        List<UUID> dinerIds,
        Boolean preferSimple,
        Boolean preferHomeOffice,
        Boolean preferMealPrep,
        List<UUID> excludedDishIds,
        Long seed) {}
