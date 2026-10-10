package de.wasessenwir.dish;

import java.util.List;
import java.util.UUID;

public record DishDetailResponse(
        UUID id,
        String name,
        String description,
        List<MealType> mealTypes,
        List<String> categories,
        Effort effort,
        List<String> ingredients,
        String recipeText,
        String recipeUrl,
        String recipeSource,
        String comments,
        Suitability mealPrep,
        Suitability homeOffice,
        Integer myRating,
        List<VariantResponse> variants) {
}
