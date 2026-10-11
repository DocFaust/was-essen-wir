package de.wasessenwir.ai;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

final class DishDuplicateFinder {

    private static final double THRESHOLD = 0.72;

    private DishDuplicateFinder() {
    }

    static List<DishDraftResponse.SimilarDish> find(String proposedName, CatalogIndex catalog, UUID excludeDishId) {
        if (proposedName == null || proposedName.isBlank()) {
            return List.of();
        }
        List<Scored> scored = new ArrayList<>();
        for (CatalogIndex.DishRef dish : catalog.dishes()) {
            if (dish.id().equals(excludeDishId)) {
                continue;
            }
            double score = score(proposedName, dish.name());
            if (score >= THRESHOLD) {
                scored.add(new Scored(
                        dish.id(),
                        null,
                        dish.name(),
                        "Ein ähnliches Gericht existiert bereits: " + dish.name() + ".",
                        score));
            }
        }
        for (CatalogIndex.VariantRef variant : catalog.variants()) {
            String dishName = catalog.dishes().stream()
                    .filter(dish -> dish.id().equals(variant.dishId()))
                    .map(CatalogIndex.DishRef::name)
                    .findFirst()
                    .orElse(variant.name());
            double score = Math.max(score(proposedName, variant.name()), score(proposedName, dishName + " " + variant.name()));
            if (score >= THRESHOLD) {
                String label = dishName + ", " + variant.name();
                scored.add(new Scored(
                        variant.dishId(),
                        variant.id(),
                        label,
                        "Eine ähnliche Variante existiert bereits: " + label + ".",
                        score));
            }
        }
        return scored.stream()
                .sorted(Comparator.comparingDouble(Scored::score).reversed())
                .limit(5)
                .map(hit -> new DishDraftResponse.SimilarDish(hit.dishId(), hit.variantId(), hit.name(), hit.message()))
                .toList();
    }

    static double score(String left, String right) {
        String foldedLeft = DraftText.fold(left);
        String foldedRight = DraftText.fold(right);
        if (foldedLeft.isEmpty() || foldedRight.isEmpty()) {
            return 0;
        }
        if (foldedLeft.equals(foldedRight)) {
            return 1;
        }
        List<String> leftTokens = DraftText.significant(left);
        List<String> rightTokens = DraftText.significant(right);
        if (leftTokens.isEmpty() || rightTokens.isEmpty()) {
            return 0;
        }
        int matched = 0;
        for (String token : leftTokens) {
            if (rightTokens.stream().anyMatch(other -> tokensMatch(token, other))) {
                matched++;
            }
        }
        boolean allLeft = matched == leftTokens.size();
        boolean allRight = rightTokens.stream().allMatch(token -> leftTokens.stream().anyMatch(other -> tokensMatch(token, other)));
        if (allLeft && allRight) {
            return 0.95;
        }
        List<String> smaller = leftTokens.size() <= rightTokens.size() ? leftTokens : rightTokens;
        boolean smallerCovered = smaller.stream().allMatch(token -> {
            List<String> other = smaller == leftTokens ? rightTokens : leftTokens;
            return other.stream().anyMatch(candidate -> tokensMatch(token, candidate));
        });
        if (smallerCovered && smaller.stream().anyMatch(token -> token.length() >= 6)) {
            return 0.8;
        }
        return matched / (double) Math.max(leftTokens.size(), rightTokens.size());
    }

    private static boolean tokensMatch(String left, String right) {
        if (left.equals(right)) {
            return true;
        }
        String shorter = left.length() <= right.length() ? left : right;
        String longer = left.length() <= right.length() ? right : left;
        if (shorter.length() >= 4 && longer.startsWith(shorter)) {
            return true;
        }
        return shorter.length() >= 6 && longer.contains(shorter);
    }

    private record Scored(UUID dishId, UUID variantId, String name, String message, double score) {
    }
}
