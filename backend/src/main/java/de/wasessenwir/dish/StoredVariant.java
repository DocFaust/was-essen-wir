package de.wasessenwir.dish;

import java.time.OffsetDateTime;
import java.util.UUID;

record StoredVariant(
        UUID id,
        UUID dishId,
        String name,
        String description,
        Effort effort,
        boolean ingredientsSpecified,
        String recipeText,
        String recipeUrl,
        String recipeSource,
        Boolean mealPrep,
        Boolean homeOffice,
        int position,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {
}
