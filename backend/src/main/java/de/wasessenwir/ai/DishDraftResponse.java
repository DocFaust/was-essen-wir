package de.wasessenwir.ai;

import de.wasessenwir.dish.Effort;
import de.wasessenwir.dish.MealType;
import java.util.List;
import java.util.UUID;

public record DishDraftResponse(
        int schemaVersion,
        DraftOutcome outcome,
        DraftKind kind,
        UUID baseDishId,
        String baseDishName,
        String name,
        String description,
        List<MealType> mealTypes,
        List<String> categories,
        Effort effort,
        List<String> ingredients,
        Boolean ingredientsSpecified,
        String recipeText,
        String recipeUrl,
        String recipeSource,
        String comments,
        Boolean mealPrep,
        Boolean homeOffice,
        List<String> recognized,
        List<String> unknown,
        List<String> warnings,
        List<SimilarDish> duplicates,
        List<BaseDishChoice> candidates) {

    public enum DraftOutcome {
        DRAFT,
        NEEDS_BASE_DISH
    }

    public record SimilarDish(UUID dishId, UUID variantId, String name, String message) {
    }

    public record BaseDishChoice(UUID id, String name) {
    }
}
