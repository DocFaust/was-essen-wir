package de.wasessenwir.dish;

import java.util.List;

public record DishWriteRequest(
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
        Boolean confirmDuplicate) {
}
