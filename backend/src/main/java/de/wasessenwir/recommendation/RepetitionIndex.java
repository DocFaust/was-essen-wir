package de.wasessenwir.recommendation;

import de.wasessenwir.meal.MealOccurrence;
import java.time.LocalDate;
import java.util.List;

public record RepetitionIndex(LocalDate referenceDate, List<MealOccurrence> occurrences) {

    public RepetitionIndex {
        occurrences = List.copyOf(occurrences);
    }

    public static RepetitionIndex empty() {
        return new RepetitionIndex(LocalDate.of(2000, 1, 1), List.of());
    }
}
