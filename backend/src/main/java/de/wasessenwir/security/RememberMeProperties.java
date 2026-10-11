package de.wasessenwir.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.remember-me")
public record RememberMeProperties(boolean enabled, Duration duration) {

    public RememberMeProperties {
        if (duration == null || duration.compareTo(Duration.ofHours(1)) < 0 || duration.compareTo(Duration.ofDays(366)) > 0) {
            throw new IllegalArgumentException("app.remember-me.duration muss zwischen einer Stunde und 366 Tagen liegen.");
        }
    }
}
