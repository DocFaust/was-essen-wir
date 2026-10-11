package de.wasessenwir.ai;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

final class BaseDishResolver {

    private BaseDishResolver() {
    }

    static List<CatalogIndex.DishRef> resolve(String description, List<CatalogIndex.DishRef> dishes) {
        String folded = DraftText.fold(description);
        LinkedHashMap<UUID, CatalogIndex.DishRef> hits = new LinkedHashMap<>();
        for (CatalogIndex.DishRef dish : dishes) {
            if (phraseMatch(folded, dish.name()) || distinctiveToken(folded, dish.name())) {
                hits.put(dish.id(), dish);
            }
        }
        return List.copyOf(hits.values());
    }

    private static boolean phraseMatch(String foldedDescription, String dishName) {
        String foldedName = DraftText.fold(dishName);
        if (foldedName.length() >= 3 && (DraftText.mentioned(foldedDescription, foldedName))) {
            return true;
        }
        List<String> tokens = DraftText.significant(dishName);
        if (tokens.isEmpty() || tokens.stream().noneMatch(token -> token.length() >= 5)) {
            return false;
        }
        return tokens.stream().allMatch(token -> DraftText.mentioned(foldedDescription, token));
    }

    private static boolean distinctiveToken(String foldedDescription, String dishName) {
        return DraftText.significant(dishName).stream()
                .anyMatch(token -> token.length() >= 6 && DraftText.mentioned(foldedDescription, token));
    }

    static List<CatalogIndex.DishRef> sorted(List<CatalogIndex.DishRef> dishes) {
        return dishes.stream()
                .sorted(Comparator.comparing(dish -> DraftText.fold(dish.name())))
                .toList();
    }
}
