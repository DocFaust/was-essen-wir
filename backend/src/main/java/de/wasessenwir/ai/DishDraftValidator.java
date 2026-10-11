package de.wasessenwir.ai;

import de.wasessenwir.dish.Effort;
import de.wasessenwir.dish.MealType;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class DishDraftValidator {

    static final int VERSION = 1;
    static final int MAX_CONTENT = 8_000;
    private static final int MAX_ITEMS = 50;
    private static final int MAX_ITEM = 120;
    private static final int MAX_WARNING = 300;

    private final ObjectMapper mapper;

    public DishDraftValidator(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public DishDraftFacts accept(String content, String description, DraftKind kind) {
        JsonNode node = read(content);
        ModelDraft model = model(node);
        DishDraftFacts facts = DishDraftFacts.read(description, kind);
        List<String> extra = new ArrayList<>();
        if (dropped(model, facts, description)) {
            extra.add("Angaben ohne Beleg in der Beschreibung wurden nicht übernommen.");
        }
        String name = facts.name() != null ? facts.name() : supportedName(model.name(), description);
        if (name == null) {
            throw new DishDraftRejectedException(
                    "Daraus lässt sich kein Gerichtsname erkennen. Du kannst das Gericht manuell erfassen.");
        }
        return facts.enrich(
                first(facts.description(), supportedText(model.description(), description, 4_000)),
                first(facts.recipeText(), supportedText(model.recipeText(), description, 4_000)),
                first(facts.recipeUrl(), supportedUrl(model.recipeUrl(), description)),
                first(facts.recipeSource(), supportedText(model.recipeSource(), description, 500)),
                first(facts.comments(), supportedText(model.comments(), description, 500)),
                extra).withName(name);
    }

    private JsonNode read(String content) {
        if (content == null || content.isBlank() || content.length() > MAX_CONTENT) {
            throw AiProviderException.invalid();
        }
        JsonNode node;
        try {
            node = mapper.readTree(IntentValidator.strip(content));
        } catch (JacksonException exception) {
            throw AiProviderException.invalid();
        }
        if (node == null || !node.isObject() || !node.path("schemaVersion").isInt() || node.path("schemaVersion").asInt() != VERSION) {
            throw AiProviderException.invalid();
        }
        rejectIds(node);
        for (String field : List.of(
                "name", "description", "mealTypes", "categories", "effort", "ingredients", "removedIngredients",
                "recipeText", "recipeUrl", "recipeSource", "comments", "mealPrep", "homeOffice", "warnings")) {
            if (!node.has(field)) {
                throw AiProviderException.invalid();
            }
        }
        return node;
    }

    private static void rejectIds(JsonNode node) {
        for (String field : List.of("id", "dishId", "baseDishId", "variantId", "participantIds")) {
            JsonNode value = node.get(field);
            if (value != null && !value.isNull()) {
                throw AiProviderException.invalid();
            }
        }
    }

    private static ModelDraft model(JsonNode node) {
        return new ModelDraft(
                text(node.get("name"), 120),
                text(node.get("description"), 4_000),
                meals(node.get("mealTypes")),
                texts(node.get("categories"), 20, 80),
                effort(node.get("effort")),
                texts(node.get("ingredients"), MAX_ITEMS, MAX_ITEM),
                texts(node.get("removedIngredients"), 20, MAX_ITEM),
                text(node.get("recipeText"), 4_000),
                text(node.get("recipeUrl"), 2_000),
                text(node.get("recipeSource"), 500),
                text(node.get("comments"), 500),
                tri(node.get("mealPrep")),
                tri(node.get("homeOffice")),
                texts(node.get("warnings"), 8, MAX_WARNING));
    }

    private static boolean dropped(ModelDraft model, DishDraftFacts facts, String description) {
        String folded = DraftText.fold(description);
        if (model.effort() != null && model.effort() != facts.effort()) {
            return true;
        }
        if (model.mealPrep() != null && !model.mealPrep().equals(facts.mealPrep())) {
            return true;
        }
        if (model.homeOffice() != null && !model.homeOffice().equals(facts.homeOffice())) {
            return true;
        }
        for (MealType type : model.mealTypes()) {
            if (!facts.mealTypes().contains(type)) {
                return true;
            }
        }
        for (String ingredient : model.ingredients()) {
            if (!containsFolded(facts.ingredients(), ingredient) && !DraftText.mentioned(folded, ingredient)) {
                return true;
            }
        }
        if (model.name() == null) {
            return false;
        }
        if (facts.name() != null && DraftText.fold(model.name()).equals(DraftText.fold(facts.name()))) {
            return false;
        }
        return supportedName(model.name(), description) == null;
    }

    private static boolean containsFolded(List<String> values, String candidate) {
        String folded = DraftText.fold(candidate);
        for (String value : values) {
            if (DraftText.fold(value).equals(folded)) {
                return true;
            }
        }
        return false;
    }

    private static String supportedName(String name, String description) {
        if (name == null) {
            return null;
        }
        List<String> tokens = DraftText.significant(name);
        if (tokens.isEmpty()) {
            return null;
        }
        String folded = DraftText.fold(description);
        for (String token : tokens) {
            if (!DraftText.mentioned(folded, token)) {
                return null;
            }
        }
        return name;
    }

    private static String supportedText(String value, String description, int max) {
        if (value == null || value.isBlank() || value.length() > max || value.contains("://") || control(value)) {
            return null;
        }
        String folded = DraftText.fold(description);
        for (String token : DraftText.significant(value)) {
            if (token.length() >= 4 && !DraftText.mentioned(folded, token)) {
                return null;
            }
        }
        return value.trim();
    }

    private static String supportedUrl(String value, String description) {
        if (value == null || value.isBlank() || !description.contains(value.trim())) {
            return null;
        }
        String url = value.trim();
        if (url.length() > 2_000 || !(url.startsWith("http://") || url.startsWith("https://"))) {
            return null;
        }
        return url;
    }

    private static String first(String preferred, String fallback) {
        return preferred != null ? preferred : fallback;
    }

    private static String text(JsonNode value, int max) {
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isString()) {
            throw AiProviderException.invalid();
        }
        String text = value.asString().trim();
        if (text.isEmpty()) {
            return null;
        }
        if (text.length() > max || control(text)) {
            throw AiProviderException.invalid();
        }
        return text;
    }

    private static Effort effort(JsonNode value) {
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isString()) {
            throw AiProviderException.invalid();
        }
        try {
            return Effort.valueOf(value.asString());
        } catch (IllegalArgumentException exception) {
            throw AiProviderException.invalid();
        }
    }

    private static Boolean tri(JsonNode value) {
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isBoolean()) {
            throw AiProviderException.invalid();
        }
        return value.asBoolean();
    }

    private static List<MealType> meals(JsonNode value) {
        if (value == null || value.isNull() || !value.isArray() || value.size() > 4) {
            throw AiProviderException.invalid();
        }
        List<MealType> types = new ArrayList<>();
        for (JsonNode item : value) {
            if (!item.isString()) {
                throw AiProviderException.invalid();
            }
            try {
                MealType type = MealType.valueOf(item.asString());
                if (!types.contains(type)) {
                    types.add(type);
                }
            } catch (IllegalArgumentException exception) {
                throw AiProviderException.invalid();
            }
        }
        return List.copyOf(types);
    }

    private static List<String> texts(JsonNode value, int maxItems, int maxLength) {
        if (value == null || value.isNull() || !value.isArray() || value.size() > maxItems) {
            throw AiProviderException.invalid();
        }
        List<String> items = new ArrayList<>();
        for (JsonNode item : value) {
            if (!item.isString()) {
                throw AiProviderException.invalid();
            }
            String text = item.asString().trim();
            if (text.isEmpty() || text.length() > maxLength || control(text)) {
                throw AiProviderException.invalid();
            }
            items.add(text);
        }
        return List.copyOf(items);
    }

    private static boolean control(String text) {
        return text.chars().anyMatch(Character::isISOControl);
    }

    private record ModelDraft(
            String name,
            String description,
            List<MealType> mealTypes,
            List<String> categories,
            Effort effort,
            List<String> ingredients,
            List<String> removedIngredients,
            String recipeText,
            String recipeUrl,
            String recipeSource,
            String comments,
            Boolean mealPrep,
            Boolean homeOffice,
            List<String> warnings) {
    }
}
