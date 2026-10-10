package de.wasessenwir.dish;

import java.time.OffsetDateTime;
import java.util.UUID;

record StoredDish(
        UUID id,
        String name,
        String description,
        Effort effort,
        String recipeText,
        String recipeUrl,
        String recipeSource,
        String comments,
        Boolean mealPrep,
        Boolean homeOffice,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {
}
