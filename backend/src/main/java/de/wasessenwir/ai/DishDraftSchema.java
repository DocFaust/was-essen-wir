package de.wasessenwir.ai;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

final class DishDraftSchema {

    static final String JSON = """
            {
              "type": "object",
              "additionalProperties": false,
              "properties": {
                "schemaVersion": { "type": "integer", "enum": [1] },
                "name": { "anyOf": [ { "type": "string" }, { "type": "null" } ] },
                "description": { "anyOf": [ { "type": "string" }, { "type": "null" } ] },
                "mealTypes": {
                  "type": "array",
                  "items": { "type": "string", "enum": ["FRUEHSTUECK", "MITTAGESSEN", "ABENDESSEN", "SNACK"] }
                },
                "categories": { "type": "array", "items": { "type": "string" } },
                "effort": {
                  "anyOf": [
                    { "type": "string", "enum": ["EINFACH", "MITTEL", "AUFWENDIG"] },
                    { "type": "null" }
                  ]
                },
                "ingredients": { "type": "array", "items": { "type": "string" } },
                "removedIngredients": { "type": "array", "items": { "type": "string" } },
                "recipeText": { "anyOf": [ { "type": "string" }, { "type": "null" } ] },
                "recipeUrl": { "anyOf": [ { "type": "string" }, { "type": "null" } ] },
                "recipeSource": { "anyOf": [ { "type": "string" }, { "type": "null" } ] },
                "comments": { "anyOf": [ { "type": "string" }, { "type": "null" } ] },
                "mealPrep": { "anyOf": [ { "type": "boolean" }, { "type": "null" } ] },
                "homeOffice": { "anyOf": [ { "type": "boolean" }, { "type": "null" } ] },
                "warnings": { "type": "array", "items": { "type": "string" } }
              },
              "required": [
                "schemaVersion", "name", "description", "mealTypes", "categories", "effort",
                "ingredients", "removedIngredients", "recipeText", "recipeUrl", "recipeSource",
                "comments", "mealPrep", "homeOffice", "warnings"
              ]
            }
            """;

    private DishDraftSchema() {
    }

    static JsonNode tree(ObjectMapper mapper) {
        try {
            return mapper.readTree(JSON);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Das JSON-Schema für Gerichteentwürfe ist ungültig.");
        }
    }
}
