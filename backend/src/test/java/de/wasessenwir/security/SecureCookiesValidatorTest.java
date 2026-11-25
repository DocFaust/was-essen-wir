package de.wasessenwir.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class SecureCookiesValidatorTest {

    @Test
    void acceptsSecureCookiesWhenRequired() {
        SecureCookiesValidator validator = new SecureCookiesValidator(properties(true), true);
        assertThatCode(validator::validate).doesNotThrowAnyException();
    }

    @Test
    void rejectsInsecureCookiesWhenRequired() {
        SecureCookiesValidator validator = new SecureCookiesValidator(properties(true), false);
        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APP_SESSION_COOKIE_SECURE");
    }

    @Test
    void allowsInsecureCookiesWhenNotRequired() {
        SecureCookiesValidator validator = new SecureCookiesValidator(properties(false), false);
        assertThatCode(validator::validate).doesNotThrowAnyException();
    }

    private static SecurityProperties properties(boolean requireSecure) {
        return new SecurityProperties(
                8, Duration.ofMinutes(15), 8, Duration.ofMinutes(15), 100, requireSecure, false, "");
    }
}
