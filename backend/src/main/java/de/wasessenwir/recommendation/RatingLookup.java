package de.wasessenwir.recommendation;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class RatingLookup {

    private final Map<UUID, Map<UUID, Integer>> dishStars;
    private final Map<UUID, Map<UUID, Integer>> variantStars;

    private RatingLookup(Map<UUID, Map<UUID, Integer>> dishStars, Map<UUID, Map<UUID, Integer>> variantStars) {
        this.dishStars = dishStars;
        this.variantStars = variantStars;
    }

    public static RatingLookup empty() {
        return new RatingLookup(Map.of(), Map.of());
    }

    public static RatingLookup from(List<RatingEntry> entries) {
        Map<UUID, Map<UUID, Integer>> dishes = new HashMap<>();
        Map<UUID, Map<UUID, Integer>> variants = new HashMap<>();
        for (RatingEntry entry : entries) {
            if (entry.dishId() != null) {
                dishes.computeIfAbsent(entry.userId(), ignored -> new HashMap<>()).put(entry.dishId(), entry.stars());
            }
            if (entry.variantId() != null) {
                variants.computeIfAbsent(entry.userId(), ignored -> new HashMap<>()).put(entry.variantId(), entry.stars());
            }
        }
        return new RatingLookup(freeze(dishes), freeze(variants));
    }

    public Integer dishStars(UUID userId, UUID dishId) {
        return dishStars.getOrDefault(userId, Map.of()).get(dishId);
    }

    public Integer variantStars(UUID userId, UUID variantId) {
        return variantStars.getOrDefault(userId, Map.of()).get(variantId);
    }

    private static Map<UUID, Map<UUID, Integer>> freeze(Map<UUID, Map<UUID, Integer>> source) {
        Map<UUID, Map<UUID, Integer>> copy = new HashMap<>();
        source.forEach((userId, stars) -> copy.put(userId, Map.copyOf(stars)));
        return Map.copyOf(copy);
    }
}
