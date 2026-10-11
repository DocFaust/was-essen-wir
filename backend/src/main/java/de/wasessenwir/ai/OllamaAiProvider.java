package de.wasessenwir.ai;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import java.net.http.HttpClient;
import java.util.Map;

final class OllamaAiProvider implements AiProvider {

    private final AiProperties.Ollama properties;
    private final ObjectMapper mapper;
    private final HttpClient client;

    OllamaAiProvider(AiProperties.Ollama properties, ObjectMapper mapper) {
        this.properties = properties;
        this.mapper = mapper;
        this.client = HttpClient.newBuilder()
                .connectTimeout(ProviderHttp.timeout(properties.timeout()))
                .build();
    }

    @Override
    public String interpret(String systemPrompt, String userMessage) {
        if (properties.model() == null || properties.model().isBlank()) {
            throw AiProviderException.unreachable();
        }
        ObjectNode body = mapper.createObjectNode();
        body.put("model", properties.model().trim());
        body.put("stream", false);
        body.put("format", "json");
        body.put("think", false);
        body.putObject("options").put("temperature", 0);
        var messages = body.putArray("messages");
        messages.addObject().put("role", "system").put("content", systemPrompt);
        messages.addObject().put("role", "user").put("content", userMessage);
        String response = ProviderHttp.post(
                client,
                ProviderHttp.endpoint(properties.baseUrl(), "/api/chat"),
                ProviderHttp.timeout(properties.timeout()),
                mapper.writeValueAsString(body),
                Map.of());
        return content(response);
    }

    private String content(String response) {
        try {
            JsonNode root = mapper.readTree(response);
            JsonNode content = root.path("message").path("content");
            if (content.isString()) {
                return content.asString();
            }
            if (content.isObject()) {
                return content.toString();
            }
        } catch (JacksonException exception) {
            throw AiProviderException.invalid();
        }
        throw AiProviderException.invalid();
    }
}
