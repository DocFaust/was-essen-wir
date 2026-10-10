package de.wasessenwir.meal;

import de.wasessenwir.dish.MealType;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record StoredMealEvent(
        UUID id,
        LocalDate mealDate,
        MealType mealType,
        MealStatus status,
        UUID dishId,
        UUID variantId,
        String dishName,
        String variantName,
        String comment,
        UUID createdBy,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        List<StoredParticipant> participants) {

    public record StoredParticipant(UUID userId, String displayName) {}
}
