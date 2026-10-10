package de.wasessenwir.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security")
public record SecurityProperties(
        int loginMaxAttempts,
        Duration loginWindow,
        int setupMaxAttempts,
        Duration setupWindow) {}
