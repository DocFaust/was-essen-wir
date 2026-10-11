package de.wasessenwir.ai;

import java.util.List;
import java.util.UUID;

public record CatalogIndex(
        List<DishRef> dishes,
        List<VariantRef> variants,
        List<TagRef> categories,
        List<IngredientRef> ingredients) {

    public record DishRef(UUID id, String name) {}

    public record VariantRef(UUID id, UUID dishId, String name) {}

    public record TagRef(UUID dishId, String name) {}

    public record IngredientRef(UUID dishId, UUID variantId, String name) {}
}
