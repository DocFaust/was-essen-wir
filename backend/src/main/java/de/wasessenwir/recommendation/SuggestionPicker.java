package de.wasessenwir.recommendation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.ToDoubleFunction;
import java.util.random.RandomGenerator;

public final class SuggestionPicker {

    private SuggestionPicker() {}

    public static List<ScoredCandidate> pick(List<ScoredCandidate> scored, RandomGenerator random, int limit) {
        List<Family> families = families(scored);
        List<ScoredCandidate> picked = new ArrayList<>();
        while (picked.size() < limit && !families.isEmpty()) {
            Family family = weighted(families, Family::weight, random);
            families.remove(family);
            picked.add(weighted(family.members(), ScoredCandidate::selectionWeight, random));
        }
        return List.copyOf(picked);
    }

    private static List<Family> families(List<ScoredCandidate> scored) {
        Map<UUID, List<ScoredCandidate>> grouped = new LinkedHashMap<>();
        for (ScoredCandidate candidate : scored) {
            grouped.computeIfAbsent(candidate.candidate().dishId(), ignored -> new ArrayList<>()).add(candidate);
        }
        List<Family> families = new ArrayList<>();
        for (List<ScoredCandidate> members : grouped.values()) {
            List<ScoredCandidate> ordered = new ArrayList<>(members);
            ordered.sort(Comparator
                    .comparing((ScoredCandidate candidate) -> candidate.candidate().variantId() == null ? 0 : 1)
                    .thenComparing(
                            candidate -> candidate.candidate().variantName() == null ? "" : candidate.candidate().variantName(),
                            String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(candidate -> String.valueOf(candidate.candidate().variantId())));
            double weight = ordered.stream().mapToDouble(ScoredCandidate::selectionWeight).max().orElseThrow();
            Candidate sample = ordered.getFirst().candidate();
            families.add(new Family(sample.dishId(), sample.dishName(), List.copyOf(ordered), weight));
        }
        families.sort(Comparator
                .comparing(Family::dishName, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(Family::dishId));
        return families;
    }

    private static <T> T weighted(List<T> items, ToDoubleFunction<T> weight, RandomGenerator random) {
        double total = 0;
        for (T item : items) {
            double value = weight.applyAsDouble(item);
            if (value <= 0) {
                throw new IllegalStateException("Auswahlgewicht muss positiv sein.");
            }
            total += value;
        }
        double cursor = random.nextDouble() * total;
        double cumulative = 0;
        for (T item : items) {
            cumulative += weight.applyAsDouble(item);
            if (cursor < cumulative) {
                return item;
            }
        }
        return items.getLast();
    }

    private record Family(UUID dishId, String dishName, List<ScoredCandidate> members, double weight) {}
}
