package de.wasessenwir.meal;

import de.wasessenwir.dish.MealType;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record MealEventResponse(
        UUID id,
        LocalDate mealDate,
        MealType mealType,
        MealStatus status,
        UUID dishId,
        UUID variantId,
        String dishName,
        String variantName,
        String name,
        String comment,
        List<MealParticipantResponse> participants,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {}
