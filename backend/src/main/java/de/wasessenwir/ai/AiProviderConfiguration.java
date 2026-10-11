package de.wasessenwir.ai;

import tools.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.Locale;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiProviderConfiguration {

    @Bean
    AiProvider aiProvider(AiProperties properties, ObjectMapper objectMapper, Clock clock) {
        if (!properties.enabled()) {
            return new DisabledAiProvider();
        }
        String provider = properties.provider() == null ? "" : properties.provider().trim().toLowerCase(Locale.ROOT);
        return switch (provider) {
            case "ollama" -> new OllamaAiProvider(properties.ollama(), objectMapper);
            case "openai" -> new OpenAiAiProvider(properties.openai(), objectMapper);
            case "fixture" -> new FixtureAiProvider(clock, objectMapper);
            default -> throw new IllegalStateException("Unbekannter KI-Provider.");
        };
    }
}
