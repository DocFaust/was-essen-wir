package de.wasessenwir.ai;

import de.wasessenwir.dish.MealType;
import java.util.List;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

final class DishDraftJson {

    private DishDraftJson() {
    }

    static String write(ObjectMapper mapper, DishDraftFacts facts) {
        ObjectNode node = mapper.createObjectNode();
        node.put("schemaVersion", DishDraftValidator.VERSION);
        text(node, "name", facts.name());
        text(node, "description", facts.description());
        ArrayNode meals = node.putArray("mealTypes");
        for (MealType mealType : facts.mealTypes()) {
            meals.add(mealType.name());
        }
        strings(node.putArray("categories"), facts.categories());
        if (facts.effort() == null) {
            node.putNull("effort");
        } else {
            node.put("effort", facts.effort().name());
        }
        strings(node.putArray("ingredients"), facts.ingredients());
        strings(node.putArray("removedIngredients"), facts.removedIngredients());
        text(node, "recipeText", facts.recipeText());
        text(node, "recipeUrl", facts.recipeUrl());
        text(node, "recipeSource", facts.recipeSource());
        text(node, "comments", facts.comments());
        bool(node, "mealPrep", facts.mealPrep());
        bool(node, "homeOffice", facts.homeOffice());
        strings(node.putArray("warnings"), facts.warnings());
        return mapper.writeValueAsString(node);
    }

    private static void text(ObjectNode node, String field, String value) {
        if (value == null) {
            node.putNull(field);
        } else {
            node.put(field, value);
        }
    }

    private static void bool(ObjectNode node, String field, Boolean value) {
        if (value == null) {
            node.putNull(field);
        } else {
            node.put(field, value);
        }
    }

    private static void strings(ArrayNode array, List<String> values) {
        for (String value : values) {
            array.add(value);
        }
    }
}
