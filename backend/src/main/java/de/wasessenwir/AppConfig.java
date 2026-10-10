package de.wasessenwir;

import de.wasessenwir.security.SecurityProperties;
import de.wasessenwir.setup.SetupProperties;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({SetupProperties.class, SecurityProperties.class})
public class AppConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
