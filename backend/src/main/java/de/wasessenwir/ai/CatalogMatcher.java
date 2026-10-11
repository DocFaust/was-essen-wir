package de.wasessenwir.ai;

import de.wasessenwir.recommendation.CatalogSelection;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class CatalogMatcher {

    private CatalogMatcher() {}

    public record Match(
            CatalogSelection selection,
            Set<UUID> excludedDishIds,
            List<String> matchedDishes,
            List<String> limitations,
            boolean nothingFound) {}

    public static Match match(CatalogIndex index, List<String> searchTerms, List<String> avoidTerms) {
        List<String> limitations = new ArrayList<>();
        Set<UUID> excluded = new LinkedHashSet<>();
        for (String term : avoidTerms) {
            Set<UUID> families = families(index, term);
            if (families.isEmpty()) {
                limitations.add("„" + term + "“ steht so nicht in der Sammlung und wurde nicht ausgeschlossen.");
            } else {
                excluded.addAll(families);
            }
        }

        Set<UUID> dishes = new LinkedHashSet<>();
        Set<UUID> variants = new LinkedHashSet<>();
        List<String> labels = new ArrayList<>();
        boolean anyHit = false;
        for (String term : searchTerms) {
            boolean hit = search(index, term, dishes, variants, labels);
            if (!hit) {
                limitations.add("„" + term
                        + "“ ließ sich keinem Gericht, keiner Variante, keiner Kategorie und keiner gespeicherten Zutat zuordnen.");
            } else {
                anyHit = true;
            }
        }
        boolean nothingFound = !searchTerms.isEmpty() && !anyHit;
        CatalogSelection selection = searchTerms.isEmpty() || nothingFound
                ? CatalogSelection.all()
                : CatalogSelection.only(dishes, variants);
        return new Match(selection, Set.copyOf(excluded), distinct(labels), List.copyOf(limitations), nothingFound);
    }

    private static boolean search(
            CatalogIndex index, String term, Set<UUID> dishes, Set<UUID> variants, List<String> labels) {
        String needle = TextMatch.fold(term);
        boolean hit = false;
        for (CatalogIndex.DishRef dish : index.dishes()) {
            if (TextMatch.containsTerm(TextMatch.fold(dish.name()), needle)) {
                dishes.add(dish.id());
                labels.add(dish.name());
                hit = true;
            }
        }
        for (CatalogIndex.TagRef category : index.categories()) {
            if (TextMatch.containsTerm(TextMatch.fold(category.name()), needle)) {
                dishes.add(category.dishId());
                labels.add(dishName(index, category.dishId()));
                hit = true;
            }
        }
        for (CatalogIndex.IngredientRef ingredient : index.ingredients()) {
            if (ingredient.variantId() != null || !TextMatch.containsTerm(TextMatch.fold(ingredient.name()), needle)) {
                continue;
            }
            dishes.add(ingredient.dishId());
            labels.add(dishName(index, ingredient.dishId()));
            hit = true;
        }
        for (CatalogIndex.VariantRef variant : index.variants()) {
            if (dishes.contains(variant.dishId())) {
                continue;
            }
            if (TextMatch.containsTerm(TextMatch.fold(variant.name()), needle)) {
                variants.add(variant.id());
                labels.add(dishName(index, variant.dishId()) + " · " + variant.name());
                hit = true;
            }
        }
        for (CatalogIndex.IngredientRef ingredient : index.ingredients()) {
            if (ingredient.variantId() == null || dishes.contains(ingredient.dishId())) {
                continue;
            }
            if (!TextMatch.containsTerm(TextMatch.fold(ingredient.name()), needle)) {
                continue;
            }
            variants.add(ingredient.variantId());
            labels.add(labelForVariant(index, ingredient.variantId(), ingredient.dishId()));
            hit = true;
        }
        return hit;
    }

    private static Set<UUID> families(CatalogIndex index, String term) {
        Set<UUID> dishes = new LinkedHashSet<>();
        Set<UUID> variants = new LinkedHashSet<>();
        search(index, term, dishes, variants, new ArrayList<>());
        for (CatalogIndex.VariantRef variant : index.variants()) {
            if (variants.contains(variant.id())) {
                dishes.add(variant.dishId());
            }
        }
        return dishes;
    }

    private static String labelForVariant(CatalogIndex index, UUID variantId, UUID dishId) {
        String variantName = index.variants().stream()
                .filter(variant -> variant.id().equals(variantId))
                .map(CatalogIndex.VariantRef::name)
                .findFirst()
                .orElse("");
        return dishName(index, dishId) + " · " + variantName;
    }

    private static String dishName(CatalogIndex index, UUID dishId) {
        return index.dishes().stream()
                .filter(dish -> dish.id().equals(dishId))
                .map(CatalogIndex.DishRef::name)
                .findFirst()
                .orElse("Gericht");
    }

    private static List<String> distinct(List<String> labels) {
        return List.copyOf(new LinkedHashSet<>(labels));
    }
}
