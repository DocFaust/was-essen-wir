package de.wasessenwir.recommendation;

import java.util.Set;
import java.util.UUID;

public record CatalogSelection(boolean restricted, Set<UUID> dishIds, Set<UUID> variantIds) {

    public CatalogSelection {
        dishIds = dishIds == null ? Set.of() : Set.copyOf(dishIds);
        variantIds = variantIds == null ? Set.of() : Set.copyOf(variantIds);
    }

    public static CatalogSelection all() {
        return new CatalogSelection(false, Set.of(), Set.of());
    }

    public static CatalogSelection only(Set<UUID> dishIds, Set<UUID> variantIds) {
        return new CatalogSelection(true, dishIds, variantIds);
    }

    public boolean includes(Candidate candidate) {
        if (!restricted) {
            return true;
        }
        if (dishIds.contains(candidate.dishId())) {
            return true;
        }
        return candidate.variantId() != null && variantIds.contains(candidate.variantId());
    }
}
