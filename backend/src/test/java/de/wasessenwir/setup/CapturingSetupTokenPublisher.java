package de.wasessenwir.setup;

import java.util.concurrent.atomic.AtomicReference;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("test")
public class CapturingSetupTokenPublisher implements SetupTokenPublisher {

    private final AtomicReference<String> token = new AtomicReference<>();

    @Override
    public void publish(String value) {
        token.set(value);
    }

    public String token() {
        String value = token.get();
        if (value == null) {
            throw new IllegalStateException("Es wurde kein Setup-Token erzeugt.");
        }
        return value;
    }
}
