package de.wasessenwir.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class AttemptLimiter {

    private final ConcurrentHashMap<String, Deque<Instant>> attempts = new ConcurrentHashMap<>();
    private final Clock clock;

    public AttemptLimiter(Clock clock) {
        this.clock = clock;
    }

    public boolean tryAcquire(String key, int maxAttempts, Duration window) {
        Instant now = clock.instant();
        Deque<Instant> deque = attempts.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (deque) {
            Instant cutoff = now.minus(window);
            while (!deque.isEmpty() && deque.peekFirst().isBefore(cutoff)) {
                deque.removeFirst();
            }
            if (deque.size() >= maxAttempts) {
                return false;
            }
            deque.addLast(now);
            return true;
        }
    }

    public void reset(String key) {
        attempts.remove(key);
    }

    void clear() {
        attempts.clear();
    }
}
