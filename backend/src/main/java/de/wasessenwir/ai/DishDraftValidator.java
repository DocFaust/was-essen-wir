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
        boolean unknown = unknownDish(model.warnings());
        boolean dishCompletion = !unknown && kind == DraftKind.DISH;
        List<String> warnings = new ArrayList<>(facts.warnings());
        if (unknown) {
            addWarning(warnings, "Das Gericht war nicht eindeutig erkennbar. Es wurde kein Rezept ergänzt.");
        }
        if (kind == DraftKind.VARIANT && !model.mealTypes().isEmpty()) {
            addWarning(warnings, "Mahlzeiten bleiben beim Grundgericht und werden nicht überschrieben.");
        }
        String name = facts.name() != null ? facts.name() : plainText(model.name(), 120);
        if (name == null) {
            throw new DishDraftRejectedException(
                    "Daraus lässt sich kein Gerichtsname erkennen. Du kannst das Gericht manuell erfassen.");
        }
        List<String> removed = removed(facts, model, !unknown);
        List<String> ingredients = ingredients(kind, facts, model, removed, !unknown);
        String recipeText = unknown ? null : plainText(model.recipeText(), 4_000);
        return facts.complete(
                name,
                unknown ? null : plainText(model.description(), 4_000),
                meals(kind, facts, model, dishCompletion),
                categories(kind, facts, model, dishCompletion),
                effort(kind, facts, model, dishCompletion),
                ingredients,
                List.copyOf(removed),
                recipeText,
                first(facts.recipeUrl(), supportedUrl(model.recipeUrl(), description)),
                source(facts, model, description, recipeText),
                first(facts.comments(), supportedText(model.comments(), description, 500)),
                triState(kind, facts.mealPrep(), model.mealPrep(), dishCompletion),
                triState(kind, facts.homeOffice(), model.homeOffice(), dishCompletion),
                warnings);
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

    private static boolean unknownDish(List<String> warnings) {
        for (String warning : warnings) {
            if ("unbekannt".equals(DraftText.fold(warning))) {
                return true;
            }
        }
        return false;
    }

    private static void addWarning(List<String> warnings, String warning) {
        if (!warnings.contains(warning)) {
            warnings.add(warning);
        }
    }

    private static List<MealType> meals(DraftKind kind, DishDraftFacts facts, ModelDraft model, boolean complete) {
        if (kind == DraftKind.VARIANT) {
            return List.of();
        }
        if (!facts.mealTypes().isEmpty() || !complete) {
            return facts.mealTypes();
        }
        return model.mealTypes();
    }

    private static List<String> categories(DraftKind kind, DishDraftFacts facts, ModelDraft model, boolean complete) {
        if (kind == DraftKind.VARIANT) {
            return List.of();
        }
        if (!facts.categories().isEmpty() || !complete) {
            return facts.categories();
        }
        List<String> categories = new ArrayList<>();
        for (String category : model.categories()) {
            String cleaned = plainText(category, 80);
            if (cleaned != null && categories.stream().noneMatch(item -> DraftText.fold(item).equals(DraftText.fold(cleaned)))) {
                categories.add(cleaned);
            }
        }
        return List.copyOf(categories);
    }

    private static Effort effort(DraftKind kind, DishDraftFacts facts, ModelDraft model, boolean complete) {
        if (facts.effort() != null || kind == DraftKind.VARIANT || !complete) {
            return facts.effort();
        }
        return model.effort();
    }

    private static Boolean triState(DraftKind kind, Boolean explicit, Boolean suggested, boolean complete) {
        if (explicit != null || kind == DraftKind.VARIANT || !complete) {
            return explicit;
        }
        return suggested;
    }

    private static List<String> removed(DishDraftFacts facts, ModelDraft model, boolean suggestions) {
        List<String> removed = new ArrayList<>();
        for (String item : facts.removedIngredients()) {
            addIngredient(removed, item, List.of());
        }
        if (suggestions) {
            for (String item : model.removedIngredients()) {
                String cleaned = ingredient(item);
                if (cleaned != null && !listed(facts.ingredients(), cleaned)) {
                    addIngredient(removed, cleaned, List.of());
                }
            }
        }
        return removed;
    }

    private static List<String> ingredients(
            DraftKind kind, DishDraftFacts facts, ModelDraft model, List<String> removed, boolean suggestions) {
        List<String> ingredients = new ArrayList<>();
        boolean userListed = facts.ingredientsMentioned() && !facts.ingredients().isEmpty();
        if (userListed) {
            for (String item : facts.ingredients()) {
                addIngredient(ingredients, item, removed);
            }
        }
        boolean modelMayAdd = suggestions && !facts.exclusiveIngredients() && (kind == DraftKind.VARIANT || !userListed);
        if (modelMayAdd) {
            for (String item : model.ingredients()) {
                addIngredient(ingredients, item, removed);
            }
        }
        return List.copyOf(ingredients);
    }

    private static void addIngredient(List<String> target, String value, List<String> removed) {
        String cleaned = ingredient(value);
        if (cleaned == null || listed(removed, cleaned)) {
            return;
        }
        if (target.stream().anyMatch(item -> DraftText.fold(item).equals(DraftText.fold(cleaned)))) {
            return;
        }
        target.add(cleaned);
    }

    private static boolean listed(List<String> values, String candidate) {
        for (String value : values) {
            if (DraftText.sameIngredient(value, candidate)) {
                return true;
            }
        }
        return false;
    }

    private static String ingredient(String value) {
        if (value == null) {
            return null;
        }
        String text = value.replaceAll("(?iu)\\([^)]*\\d[^)]*\\)", " ");
        text = text.replaceAll("(?iu)^[\\d.,]+\\s*(?:mg|kg|ml|el|tl|stk|stück|stueck|bund|prise|zehe|zehen|g|l)?\\s*", "");
        text = text.replaceAll("(?iu)\\s+[\\d.,]+\\s*(?:mg|kg|ml|el|tl|stk|stück|stueck|bund|prise|zehe|zehen|g|l)?\\s*$", "");
        text = text.replaceAll("\\s+", " ").trim();
        if (text.isEmpty() || text.length() > MAX_ITEM || control(text) || text.contains("://") || text.chars().anyMatch(Character::isDigit)) {
            return null;
        }
        return text;
    }

    private static String plainText(String value, int max) {
        if (value == null || value.isBlank() || value.length() > max || value.contains("://") || control(value)) {
            return null;
        }
        return value.trim();
    }

    private static String source(DishDraftFacts facts, ModelDraft model, String description, String recipeText) {
        if (facts.recipeSource() != null) {
            return facts.recipeSource();
        }
        if (recipeText != null && generatedSource(model.recipeSource())) {
            return "KI-generiert";
        }
        String grounded = supportedText(model.recipeSource(), description, 500);
        if (generatedSource(grounded)) {
            return "KI-generiert";
        }
        return grounded;
    }

    private static boolean generatedSource(String value) {
        if (value == null) {
            return false;
        }
        String folded = DraftText.fold(value);
        return folded.equals("ki-generiert") || folded.equals("ki generiert");
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
