package de.wasessenwir.recommendation;

import java.util.UUID;

public record RatingEntry(UUID userId, UUID dishId, UUID variantId, int stars) {}
