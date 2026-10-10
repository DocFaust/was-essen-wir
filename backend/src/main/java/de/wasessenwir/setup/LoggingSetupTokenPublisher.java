package de.wasessenwir.setup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LoggingSetupTokenPublisher implements SetupTokenPublisher {

    private static final Logger log = LoggerFactory.getLogger(LoggingSetupTokenPublisher.class);

    @Override
    public void publish(String token) {
        log.warn("SETUP_TOKEN {}", token);
    }
}
