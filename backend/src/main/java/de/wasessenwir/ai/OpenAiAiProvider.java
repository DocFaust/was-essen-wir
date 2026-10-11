package de.wasessenwir.ai;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import java.net.http.HttpClient;
import java.util.Map;

final class OpenAiAiProvider implements AiProvider {

    static final String COMPLETIONS_PATH = "/v1/chat/completions";

    private static final String SCHEMA = """
            {
              "type": "object",
              "additionalProperties": false,
              "properties": {
                "version": { "type": "integer", "enum": [1] },
                "mealType": {
                  "anyOf": [
                    { "type": "string", "enum": ["FRUEHSTUECK", "MITTAGESSEN", "ABENDESSEN", "SNACK"] },
                    { "type": "null" }
                  ]
                },
                "date": { "anyOf": [ { "type": "string" }, { "type": "null" } ] },
                "participantNames": { "type": "array", "items": { "type": "string" } },
                "onlyNamedParticipants": { "type": "boolean" },
                "preferSimple": { "type": "boolean" },
                "preferHomeOffice": { "type": "boolean" },
                "preferMealPrep": { "type": "boolean" },
                "dishSearchTerms": { "type": "array", "items": { "type": "string" } },
                "avoidDishTerms": { "type": "array", "items": { "type": "string" } },
                "needsClarification": { "type": "boolean" },
                "clarificationQuestion": { "anyOf": [ { "type": "string" }, { "type": "null" } ] }
              },
              "required": [
                "version", "mealType", "date", "participantNames", "onlyNamedParticipants",
                "preferSimple", "preferHomeOffice", "preferMealPrep",
                "dishSearchTerms", "avoidDishTerms", "needsClarification", "clarificationQuestion"
              ]
            }
            """;

    private final AiProperties.OpenAi properties;
    private final ObjectMapper mapper;
    private final HttpClient client;
    private final JsonNode schema;

    OpenAiAiProvider(AiProperties.OpenAi properties, ObjectMapper mapper) {
        this.properties = properties;
        this.mapper = mapper;
        this.client = HttpClient.newBuilder()
                .connectTimeout(ProviderHttp.timeout(properties.timeout()))
                .build();
        try {
            this.schema = mapper.readTree(SCHEMA);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Das JSON-Schema des Assistenten ist ungültig.");
        }
    }

    @Override
    public String interpret(String systemPrompt, String userMessage) {
        if (properties.apiKey() == null || properties.apiKey().isBlank() || properties.model() == null || properties.model().isBlank()) {
            throw AiProviderException.unreachable();
        }
        ObjectNode body = mapper.createObjectNode();
        body.put("model", properties.model().trim());
        ObjectNode format = body.putObject("response_format");
        format.put("type", "json_schema");
        ObjectNode jsonSchema = format.putObject("json_schema");
        jsonSchema.put("name", "meal_intent");
        jsonSchema.put("strict", true);
        jsonSchema.set("schema", schema);
        var messages = body.putArray("messages");
        messages.addObject().put("role", "system").put("content", systemPrompt);
        messages.addObject().put("role", "user").put("content", userMessage);
        String response = ProviderHttp.post(
                client,
                ProviderHttp.endpoint(properties.baseUrl(), COMPLETIONS_PATH),
                ProviderHttp.timeout(properties.timeout()),
                mapper.writeValueAsString(body),
                Map.of("Authorization", "Bearer " + properties.apiKey().trim()));
        return content(response);
    }

    private String content(String response) {
        try {
            JsonNode root = mapper.readTree(response);
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                throw AiProviderException.invalid();
            }
            JsonNode content = choices.get(0).path("message").path("content");
            if (content.isString()) {
                return content.asString();
            }
            if (content.isObject()) {
                return content.toString();
            }
        } catch (AiProviderException exception) {
            throw exception;
        } catch (JacksonException exception) {
            throw AiProviderException.invalid();
        }
        throw AiProviderException.invalid();
    }
}
