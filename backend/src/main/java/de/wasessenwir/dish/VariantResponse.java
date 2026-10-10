package de.wasessenwir.dish;

import java.util.List;
import java.util.UUID;

public record VariantResponse(
        UUID id,
        String name,
        String description,
        Effort effort,
        boolean ingredientsSpecified,
        List<String> ingredients,
        String recipeText,
        String recipeUrl,
        String recipeSource,
        SuitabilitySetting mealPrep,
        SuitabilitySetting homeOffice,
        ResolvedProperties resolved) {
}
