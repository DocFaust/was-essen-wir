package de.wasessenwir.setup;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.setup")
public record SetupProperties(Duration tokenTtl, boolean rotateToken) {}
