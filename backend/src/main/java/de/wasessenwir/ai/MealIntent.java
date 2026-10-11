package de.wasessenwir.ai;

import de.wasessenwir.dish.MealType;
import java.time.LocalDate;
import java.util.List;

public record MealIntent(
        MealType mealType,
        LocalDate date,
        List<String> participantNames,
        boolean onlyNamedParticipants,
        boolean preferSimple,
        boolean preferHomeOffice,
        boolean preferMealPrep,
        List<String> dishSearchTerms,
        List<String> avoidDishTerms,
        boolean needsClarification,
        String clarificationQuestion) {}
