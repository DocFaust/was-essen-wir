package de.wasessenwir.ai;

import jakarta.annotation.PostConstruct;
import java.time.Clock;
import java.util.Arrays;
import org.springframework.core.env.Environment;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

@Configuration
public class AiProviderConfiguration {

    private final AiProperties properties;
    private final Environment environment;

    public AiProviderConfiguration(AiProperties properties, Environment environment) {
        this.properties = properties;
        this.environment = environment;
    }

    @PostConstruct
    void validate() {
        properties.validate();
        if (properties.enabled()
                && "fixture".equals(properties.normalizedProvider())
                && !fixtureAllowed()) {
            throw new IllegalStateException(
                    "Der Fixture-Provider ist nur für automatisierte Tests und kontrollierte Entwicklungsumgebungen freigegeben (Spring-Profil test oder AI_ALLOW_FIXTURE=true).");
        }
    }

    @Bean
    AiProvider aiProvider(ObjectMapper objectMapper, Clock clock) {
        if (!properties.enabled()) {
            return new DisabledAiProvider();
        }
        return switch (properties.normalizedProvider()) {
            case "ollama" -> new OllamaAiProvider(properties.ollama(), properties.maxOutputTokens(), objectMapper);
            case "openai" -> new OpenAiAiProvider(properties.openai(), properties.maxOutputTokens(), objectMapper);
            case "fixture" -> {
                if (!fixtureAllowed()) {
                    throw new IllegalStateException(
                            "Der Fixture-Provider ist nur für automatisierte Tests und kontrollierte Entwicklungsumgebungen freigegeben (Spring-Profil test oder AI_ALLOW_FIXTURE=true).");
                }
                yield new FixtureAiProvider(clock, objectMapper);
            }
            default -> throw new IllegalStateException("Unbekannter KI-Provider.");
        };
    }

    private boolean fixtureAllowed() {
        return properties.allowFixture()
                || Arrays.stream(environment.getActiveProfiles()).anyMatch("test"::equals);
    }
}
