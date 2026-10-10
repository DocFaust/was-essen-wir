package de.wasessenwir.dish;

import java.util.List;

public record VariantWriteRequest(
        String name,
        String description,
        Effort effort,
        Boolean ingredientsSpecified,
        List<String> ingredients,
        String recipeText,
        String recipeUrl,
        String recipeSource,
        SuitabilitySetting mealPrep,
        SuitabilitySetting homeOffice) {
}
