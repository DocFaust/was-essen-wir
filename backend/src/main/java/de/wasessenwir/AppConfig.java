package de.wasessenwir;

import de.wasessenwir.ai.AiProperties;
import de.wasessenwir.security.SecurityProperties;
import de.wasessenwir.setup.SetupProperties;
import java.time.Clock;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({SetupProperties.class, SecurityProperties.class, AiProperties.class})
public class AppConfig {

    @Bean
    Clock clock(@Value("${app.time-zone:Europe/Berlin}") String timeZone) {
        return Clock.system(ZoneId.of(timeZone));
    }
}
