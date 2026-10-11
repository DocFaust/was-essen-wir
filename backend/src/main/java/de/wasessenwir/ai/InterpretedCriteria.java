package de.wasessenwir.ai;

import de.wasessenwir.dish.MealType;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record InterpretedCriteria(
        MealType mealType,
        LocalDate date,
        List<UUID> dinerIds,
        List<String> participantNames,
        boolean preferSimple,
        boolean preferHomeOffice,
        boolean preferMealPrep,
        List<String> dishSearchTerms,
        List<String> avoidDishTerms,
        List<String> matchedDishes,
        List<String> limitations) {}
