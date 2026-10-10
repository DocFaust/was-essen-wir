package de.wasessenwir.dish;

import java.util.List;

record NormalizedVariant(
        String name,
        String description,
        Effort effort,
        boolean ingredientsSpecified,
        List<String> ingredients,
        String recipeText,
        String recipeUrl,
        String recipeSource,
        SuitabilitySetting mealPrep,
        SuitabilitySetting homeOffice) {
}
