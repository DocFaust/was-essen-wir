package de.wasessenwir.rating;

import java.util.UUID;

public record OwnRatingResponse(UUID dishId, UUID variantId, int stars) {}
