package de.wasessenwir.recommendation;

import de.wasessenwir.dish.Effort;
import de.wasessenwir.dish.Suitability;
import java.util.UUID;

public record Candidate(
        UUID dishId,
        String dishName,
        UUID variantId,
        String variantName,
        Effort effort,
        Suitability mealPrep,
        Suitability homeOffice) {

    public String name() {
        if (variantName == null || variantName.isBlank()) {
            return dishName;
        }
        return dishName + " · " + variantName;
    }
}
