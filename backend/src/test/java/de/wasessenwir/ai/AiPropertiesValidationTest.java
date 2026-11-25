package de.wasessenwir.ai;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class AiPropertiesValidationTest {

    @Test
    void rejectsInvalidRateLimits() {
        AiProperties properties = valid();
        properties.rateLimit().setPerUserPerMinute(0);
        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AI_RATE_LIMIT_PER_USER_PER_MINUTE");
    }

    @Test
    void rejectsConcurrentUserAboveGlobal() {
        AiProperties properties = valid();
        properties.rateLimit().setMaxConcurrentPerUser(5);
        properties.rateLimit().setMaxConcurrentGlobal(2);
        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AI_MAX_CONCURRENT_PER_USER");
    }

    @Test
    void rejectsUnknownProviderWhenEnabled() {
        AiProperties properties = valid();
        properties.setEnabled(true);
        properties.setProvider("mystery");
        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unbekannter KI-Provider");
    }

    @Test
    void rejectsFixtureOutsideTestWithoutAllowFlag() {
        AiProperties properties = valid();
        properties.setEnabled(true);
        properties.setProvider("fixture");
        properties.setAllowFixture(false);
        MockEnvironment environment = new MockEnvironment();
        AiProviderConfiguration configuration = new AiProviderConfiguration(properties, environment);
        assertThatThrownBy(configuration::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Fixture-Provider");
    }

    @Test
    void allowsFixtureWithExplicitFlag() {
        AiProperties properties = valid();
        properties.setEnabled(true);
        properties.setProvider("fixture");
        properties.setAllowFixture(true);
        MockEnvironment environment = new MockEnvironment();
        AiProviderConfiguration configuration = new AiProviderConfiguration(properties, environment);
        assertThatCode(configuration::validate).doesNotThrowAnyException();
    }

    @Test
    void allowsFixtureUnderTestProfile() {
        AiProperties properties = valid();
        properties.setEnabled(true);
        properties.setProvider("fixture");
        properties.setAllowFixture(false);
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("test");
        AiProviderConfiguration configuration = new AiProviderConfiguration(properties, environment);
        assertThatCode(configuration::validate).doesNotThrowAnyException();
    }

    @Test
    void acceptsDefaultConfiguration() {
        assertThatCode(valid()::validate).doesNotThrowAnyException();
    }

    private static AiProperties valid() {
        AiProperties properties = new AiProperties();
        properties.setEnabled(false);
        properties.setProvider("ollama");
        properties.setMaxInputLength(500);
        properties.setMaxOutputTokens(1024);
        properties.ollama().setTimeout(Duration.ofSeconds(30));
        properties.openai().setTimeout(Duration.ofSeconds(30));
        return properties;
    }
}
