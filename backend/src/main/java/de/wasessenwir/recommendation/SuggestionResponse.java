package de.wasessenwir.recommendation;

import de.wasessenwir.dish.Effort;
import de.wasessenwir.dish.Suitability;
import java.util.UUID;

public record SuggestionResponse(
        UUID dishId,
        UUID variantId,
        String name,
        String dishName,
        String variantName,
        String reason,
        Effort effort,
        Suitability mealPrep,
        Suitability homeOffice) {}
