package de.wasessenwir.setup;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class SetupBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SetupBootstrap.class);

    private final SetupRepository setups;
    private final SetupProperties properties;
    private final TransactionTemplate transactions;
    private final List<SetupTokenPublisher> publishers;
    private final Clock clock;

    public SetupBootstrap(
            SetupRepository setups,
            SetupProperties properties,
            TransactionTemplate transactions,
            List<SetupTokenPublisher> publishers,
            Clock clock) {
        this.setups = setups;
        this.properties = properties;
        this.transactions = transactions;
        this.publishers = List.copyOf(publishers);
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        initialize();
    }

    public void initialize() {
        String token = transactions.execute(status -> {
            setups.lockInstallation();
            return decide();
        });
        if (token != null) {
            for (SetupTokenPublisher publisher : publishers) {
                publisher.publish(token);
            }
        }
    }

    private String decide() {
        var current = setups.find();
        OffsetDateTime now = OffsetDateTime.now(clock);
        if (current.isEmpty()) {
            return storeNewToken(now, false);
        }
        SetupState state = current.get();
        if (state.completed()) {
            if (properties.rotateToken()) {
                log.warn("APP_SETUP_ROTATE_TOKEN wird ignoriert, weil die Einrichtung bereits abgeschlossen ist.");
            }
            return null;
        }
        boolean expired = state.expiresAt() == null || !state.expiresAt().isAfter(now);
        if (properties.rotateToken() || expired) {
            return storeNewToken(now, true);
        }
        log.info(
                "Einrichtung ist noch offen. Das bestehende Setup-Token bleibt gültig und wird nicht erneut ausgegeben. Bei Verlust APP_SETUP_ROTATE_TOKEN=true setzen und das Backend neu starten.");
        return null;
    }

    private String storeNewToken(OffsetDateTime now, boolean replace) {
        String token = TokenHasher.generate();
        byte[] hash = TokenHasher.sha256(token);
        OffsetDateTime expiresAt = now.plus(properties.tokenTtl());
        if (replace) {
            if (!setups.replaceOpenToken(hash, expiresAt, now)) {
                return null;
            }
        } else {
            setups.insertOpen(hash, expiresAt, now);
        }
        return token;
    }
}
