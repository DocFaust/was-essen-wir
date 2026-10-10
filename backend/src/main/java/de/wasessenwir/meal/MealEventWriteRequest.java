package de.wasessenwir.meal;

import de.wasessenwir.dish.MealType;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record MealEventWriteRequest(
        LocalDate mealDate,
        MealType mealType,
        UUID dishId,
        UUID variantId,
        List<UUID> dinerIds,
        String comment) {}
