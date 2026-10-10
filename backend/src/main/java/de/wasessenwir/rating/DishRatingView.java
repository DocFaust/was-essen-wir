package de.wasessenwir.rating;

import java.util.Map;
import java.util.UUID;

public record DishRatingView(Integer dishStars, Map<UUID, Integer> variantStars) {

    public Integer variantStars(UUID variantId) {
        return variantStars.get(variantId);
    }
}
