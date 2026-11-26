package de.wasessenwir.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Begrenzt Fehlversuche pro Schlüssel in einem Zeitfenster. Der Speicherverbrauch ist durch
 * {@link SecurityProperties#attemptLimiterMaxKeys()} gedeckelt. Abgelaufene Einträge werden bei
 * Zugriff und bei Kapazitätsdruck entfernt. Nicht abgelaufene Einträge werden nie zugunsten neuer
 * Schlüssel verdrängt; ist die Karte voll, werden neue Schlüssel abgelehnt ({@code false}), damit
 * bestehende Sperren nicht umgangen werden können.
 */
@Component
public class AttemptLimiter {

    static final int MAX_KEY_LENGTH = 128;

    private final ConcurrentHashMap<String, Window> attempts = new ConcurrentHashMap<>();
    private final Clock clock;
    private final int maxKeys;

    public AttemptLimiter(Clock clock, SecurityProperties properties) {
        this.clock = clock;
        this.maxKeys = Math.max(1, properties.attemptLimiterMaxKeys());
    }

    static AttemptLimiter forTests(Clock clock, int maxKeys) {
        return new AttemptLimiter(
                clock,
                new SecurityProperties(
                        8, Duration.ofMinutes(15), 30, 8, Duration.ofMinutes(15), maxKeys, false, false, ""));
    }

    public boolean tryAcquire(String key, int maxAttempts, Duration window) {
        if (maxAttempts < 1) {
            return false;
        }
        if (window == null || window.isNegative() || window.isZero()) {
            return false;
        }
        String normalized = normalizeKey(key);
        Instant now = clock.instant();
        Window existing = attempts.get(normalized);
        if (existing != null) {
            return existing.tryAcquire(now, maxAttempts, window);
        }
        if (attempts.size() >= maxKeys) {
            purgeExpired();
        }
        if (attempts.size() >= maxKeys) {
            return false;
        }
        Window created = new Window();
        Window raced = attempts.putIfAbsent(normalized, created);
        Window target = raced == null ? created : raced;
        if (raced == null && attempts.size() > maxKeys) {
            attempts.remove(normalized, created);
            return false;
        }
        return target.tryAcquire(now, maxAttempts, window);
    }

    public void reset(String key) {
        attempts.remove(normalizeKey(key));
    }

    void clear() {
        attempts.clear();
    }

    int size() {
        return attempts.size();
    }

    int maxKeys() {
        return maxKeys;
    }

    void purgeExpired() {
        Instant now = clock.instant();
        Iterator<Map.Entry<String, Window>> iterator = attempts.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Window> entry = iterator.next();
            if (entry.getValue().isExpired(now)) {
                iterator.remove();
            }
        }
    }

    static String normalizeKey(String key) {
        if (key == null) {
            return "unknown";
        }
        String trimmed = key.trim();
        if (trimmed.isEmpty()) {
            return "unknown";
        }
        if (trimmed.length() <= MAX_KEY_LENGTH) {
            return trimmed;
        }
        return trimmed.substring(0, MAX_KEY_LENGTH);
    }

    private static final class Window {

        private final Deque<Instant> stamps = new ArrayDeque<>();
        private Duration lastWindow = Duration.ofMinutes(15);

        synchronized boolean tryAcquire(Instant now, int maxAttempts, Duration window) {
            prune(now, window);
            lastWindow = window;
            if (stamps.size() >= maxAttempts) {
                return false;
            }
            stamps.addLast(now);
            return true;
        }

        synchronized boolean isExpired(Instant now) {
            prune(now, lastWindow);
            return stamps.isEmpty();
        }

        private void prune(Instant now, Duration window) {
            Instant cutoff = now.minus(window);
            while (!stamps.isEmpty() && stamps.peekFirst().isBefore(cutoff)) {
                stamps.removeFirst();
            }
        }
    }
}
