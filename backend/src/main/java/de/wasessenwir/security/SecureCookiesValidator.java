package de.wasessenwir.security;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Lehnt unsichere produktive Konfigurationen ab, wenn Secure-Cookies verpflichtend sind.
 */
@Component
public class SecureCookiesValidator {

    private final boolean requireSecureCookies;
    private final boolean secureCookie;

    public SecureCookiesValidator(
            SecurityProperties properties,
            @Value("${server.servlet.session.cookie.secure:false}") boolean secureCookie) {
        this.requireSecureCookies = properties.requireSecureCookies();
        this.secureCookie = secureCookie;
    }

    @PostConstruct
    void validate() {
        if (requireSecureCookies && !secureCookie) {
            throw new IllegalStateException(
                    "APP_REQUIRE_SECURE_COOKIES=true verlangt APP_SESSION_COOKIE_SECURE=true "
                            + "(Secure-Cookies für WAEW_SESSION, WAEW_REMEMBER und XSRF-TOKEN).");
        }
    }
}
