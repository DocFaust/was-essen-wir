package de.wasessenwir.dish;

import java.util.List;

public record ResolvedProperties(
        String description,
        boolean descriptionInherited,
        Effort effort,
        boolean effortInherited,
        List<String> ingredients,
        boolean ingredientsInherited,
        String recipeText,
        boolean recipeTextInherited,
        String recipeUrl,
        boolean recipeUrlInherited,
        String recipeSource,
        boolean recipeSourceInherited,
        Suitability mealPrep,
        boolean mealPrepInherited,
        Suitability homeOffice,
        boolean homeOfficeInherited) {
}
