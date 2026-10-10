package de.wasessenwir.meal;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

public record MealOccurrence(
        UUID dishId,
        UUID variantId,
        boolean variantSpecified,
        LocalDate mealDate,
        MealStatus status,
        Set<UUID> participantIds) {}
