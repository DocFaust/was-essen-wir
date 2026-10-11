package de.wasessenwir.ai;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import de.wasessenwir.dish.MealType;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class IntentValidator {

    static final int VERSION = 1;
    static final int MAX_CONTENT = 8_000;
    static final int MAX_NAMES = 12;
    static final int MAX_TERMS = 8;
    static final int MAX_NAME = 80;
    static final int MAX_TERM = 80;
    static final int MAX_QUESTION = 300;
    private static final LocalDate MIN_DATE = LocalDate.of(2000, 1, 1);
    private static final LocalDate MAX_DATE = LocalDate.of(2100, 12, 31);

    private final ObjectMapper mapper;

    public IntentValidator(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public MealIntent parse(String content) {
        if (content == null || content.isBlank() || content.length() > MAX_CONTENT) {
            throw AiProviderException.invalid();
        }
        String json = strip(content);
        JsonNode node;
        try {
            node = mapper.readTree(json);
        } catch (JacksonException exception) {
            throw AiProviderException.invalid();
        }
        if (node == null || !node.isObject()) {
            throw AiProviderException.invalid();
        }
        if (!node.path("version").isInt() || node.path("version").asInt() != VERSION) {
            throw AiProviderException.invalid();
        }
        rejectParticipantIds(node);
        return new MealIntent(
                mealType(node.get("mealType")),
                date(node.get("date")),
                texts(node.get("participantNames"), MAX_NAMES, MAX_NAME),
                bool(node.get("onlyNamedParticipants")),
                bool(node.get("preferSimple")),
                bool(node.get("preferHomeOffice")),
                bool(node.get("preferMealPrep")),
                texts(node.get("dishSearchTerms"), MAX_TERMS, MAX_TERM),
                texts(node.get("avoidDishTerms"), MAX_TERMS, MAX_TERM),
                bool(node.get("needsClarification")),
                question(node.get("clarificationQuestion")));
    }

    private static void rejectParticipantIds(JsonNode node) {
        JsonNode ids = node.get("participantIds");
        if (ids == null || ids.isNull()) {
            return;
        }
        if (!ids.isArray() || !ids.isEmpty()) {
            throw AiProviderException.invalid();
        }
    }

    private static MealType mealType(JsonNode value) {
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isString()) {
            throw AiProviderException.invalid();
        }
        try {
            return MealType.valueOf(value.asString());
        } catch (IllegalArgumentException exception) {
            throw AiProviderException.invalid();
        }
    }

    private static LocalDate date(JsonNode value) {
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isString()) {
            throw AiProviderException.invalid();
        }
        try {
            LocalDate date = LocalDate.parse(value.asString());
            if (date.isBefore(MIN_DATE) || date.isAfter(MAX_DATE)) {
                throw AiProviderException.invalid();
            }
            return date;
        } catch (DateTimeParseException exception) {
            throw AiProviderException.invalid();
        }
    }

    private static boolean bool(JsonNode value) {
        if (value == null || value.isNull()) {
            return false;
        }
        if (!value.isBoolean()) {
            throw AiProviderException.invalid();
        }
        return value.asBoolean();
    }

    private static String question(JsonNode value) {
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
        if (text.length() > MAX_QUESTION || forbidden(text)) {
            throw AiProviderException.invalid();
        }
        return text;
    }

    private static List<String> texts(JsonNode value, int maxItems, int maxLength) {
        if (value == null || value.isNull()) {
            return List.of();
        }
        if (!value.isArray() || value.size() > maxItems) {
            throw AiProviderException.invalid();
        }
        List<String> items = new ArrayList<>();
        for (JsonNode item : value) {
            if (!item.isString()) {
                throw AiProviderException.invalid();
            }
            String text = item.asString().trim();
            if (text.isEmpty() || text.length() > maxLength || forbidden(text)) {
                throw AiProviderException.invalid();
            }
            items.add(text);
        }
        return List.copyOf(items);
    }

    private static boolean forbidden(String text) {
        if (text.contains("://")) {
            return true;
        }
        return text.chars().anyMatch(Character::isISOControl);
    }

    static String strip(String content) {
        String withoutThink = content.replaceAll("(?s)<think>.*?</think>", "").trim();
        if (!withoutThink.startsWith("```")) {
            return withoutThink;
        }
        int firstNewline = withoutThink.indexOf('\n');
        int lastFence = withoutThink.lastIndexOf("```");
        if (firstNewline > 0 && lastFence > firstNewline) {
            return withoutThink.substring(firstNewline + 1, lastFence).trim();
        }
        return withoutThink;
    }
}
