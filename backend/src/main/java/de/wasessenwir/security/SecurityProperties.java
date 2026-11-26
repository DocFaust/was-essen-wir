package de.wasessenwir.security;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security")
public record SecurityProperties(
        int loginMaxAttempts,
        Duration loginWindow,
        int loginIpMaxAttempts,
        int setupMaxAttempts,
        Duration setupWindow,
        int attemptLimiterMaxKeys,
        boolean requireSecureCookies,
        boolean forwardClientIp,
        String trustedProxies) {

    public SecurityProperties {
        if (trustedProxies == null) {
            trustedProxies = "";
        }
    }

    public List<String> trustedProxyCidrs() {
        if (trustedProxies.isBlank()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (String part : trustedProxies.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                values.add(trimmed);
            }
        }
        return List.copyOf(values);
    }
}
