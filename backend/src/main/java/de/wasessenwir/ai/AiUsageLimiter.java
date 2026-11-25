package de.wasessenwir.ai;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.stereotype.Component;

/**
 * Gemeinsame In-Memory-Nutzungs- und Parallelitätsgrenzen für alle KI-Endpunkte einer
 * Backend-Instanz. Zähler gehen bei einem Neustart verloren. Abgelehnte Anfragen erhöhen die
 * Zähler nicht; freigegebene Kapazität wird erst nach dem Provider-Aufruf freigegeben.
 */
@Component
public class AiUsageLimiter {

    static final Duration MINUTE_WINDOW = Duration.ofMinutes(1);
    static final Duration BUSY_RETRY_AFTER = Duration.ofSeconds(5);

    private final ReentrantLock lock = new ReentrantLock();
    private final ConcurrentHashMap<UUID, UserCounters> users = new ConcurrentHashMap<>();
    private final Clock clock;
    private final AiProperties.RateLimit limits;

    private LocalDate globalDay;
    private int globalDayCount;
    private int globalConcurrent;

    public AiUsageLimiter(Clock clock, AiProperties properties) {
        this.clock = clock;
        this.limits = properties.rateLimit();
    }

    static AiUsageLimiter forTests(Clock clock, AiProperties.RateLimit limits) {
        AiProperties properties = new AiProperties();
        properties.setRateLimit(limits);
        return new AiUsageLimiter(clock, properties);
    }

    public Permit tryAcquire(UUID userId) {
        if (userId == null) {
            throw new IllegalArgumentException("Benutzerkennung fehlt.");
        }
        lock.lock();
        try {
            Instant now = clock.instant();
            LocalDate today = LocalDate.now(clock);
            ensureGlobalDay(today);

            UserCounters user = users.get(userId);
            if (user == null) {
                if (users.size() >= limits.maxTrackedUsers()) {
                    purgeIdleLocked(now, today);
                }
                if (users.size() >= limits.maxTrackedUsers()) {
                    return Permit.rejected(Rejection.GLOBAL_BUSY, BUSY_RETRY_AFTER);
                }
                UserCounters created = new UserCounters(today);
                UserCounters raced = users.putIfAbsent(userId, created);
                user = raced == null ? created : raced;
                if (raced == null && users.size() > limits.maxTrackedUsers()) {
                    users.remove(userId, created);
                    return Permit.rejected(Rejection.GLOBAL_BUSY, BUSY_RETRY_AFTER);
                }
            }

            user.ensureDay(today);
            user.pruneMinute(now);

            if (user.concurrent >= limits.maxConcurrentPerUser()) {
                return Permit.rejected(Rejection.USER_BUSY, BUSY_RETRY_AFTER);
            }
            if (globalConcurrent >= limits.maxConcurrentGlobal()) {
                return Permit.rejected(Rejection.GLOBAL_BUSY, BUSY_RETRY_AFTER);
            }
            if (user.minuteStamps.size() >= limits.perUserPerMinute()) {
                return Permit.rejected(Rejection.USER_MINUTE, retryAfterMinute(user, now));
            }
            if (user.dayCount >= limits.perUserPerDay()) {
                return Permit.rejected(Rejection.USER_DAY, retryAfterDay(today));
            }
            if (globalDayCount >= limits.globalPerDay()) {
                return Permit.rejected(Rejection.GLOBAL_DAY, retryAfterDay(today));
            }

            user.minuteStamps.addLast(now);
            user.dayCount++;
            user.concurrent++;
            globalDayCount++;
            globalConcurrent++;
            return Permit.acquired(userId, this);
        } finally {
            lock.unlock();
        }
    }

    void release(UUID userId) {
        lock.lock();
        try {
            UserCounters user = users.get(userId);
            if (user != null && user.concurrent > 0) {
                user.concurrent--;
            }
            if (globalConcurrent > 0) {
                globalConcurrent--;
            }
        } finally {
            lock.unlock();
        }
    }

    int trackedUsers() {
        return users.size();
    }

    int globalConcurrent() {
        lock.lock();
        try {
            return globalConcurrent;
        } finally {
            lock.unlock();
        }
    }

    int userConcurrent(UUID userId) {
        lock.lock();
        try {
            UserCounters user = users.get(userId);
            return user == null ? 0 : user.concurrent;
        } finally {
            lock.unlock();
        }
    }

    void clear() {
        lock.lock();
        try {
            users.clear();
            globalDay = null;
            globalDayCount = 0;
            globalConcurrent = 0;
        } finally {
            lock.unlock();
        }
    }

    private void ensureGlobalDay(LocalDate today) {
        if (globalDay == null || !globalDay.equals(today)) {
            globalDay = today;
            globalDayCount = 0;
        }
    }

    private void purgeIdleLocked(Instant now, LocalDate today) {
        if (users.size() < limits.maxTrackedUsers()) {
            return;
        }
        Iterator<Map.Entry<UUID, UserCounters>> iterator = users.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, UserCounters> entry = iterator.next();
            UserCounters counters = entry.getValue();
            counters.pruneMinute(now);
            if (counters.concurrent == 0
                    && counters.minuteStamps.isEmpty()
                    && (counters.day == null || !counters.day.equals(today) || counters.dayCount == 0)) {
                iterator.remove();
            }
        }
    }

    private Duration retryAfterMinute(UserCounters user, Instant now) {
        Instant oldest = user.minuteStamps.peekFirst();
        if (oldest == null) {
            return Duration.ofSeconds(1);
        }
        Instant unlockAt = oldest.plus(MINUTE_WINDOW);
        long seconds = Duration.between(now, unlockAt).getSeconds();
        return Duration.ofSeconds(Math.max(1, seconds));
    }

    private Duration retryAfterDay(LocalDate today) {
        ZoneId zone = clock.getZone();
        ZonedDateTime midnight = today.plusDays(1).atStartOfDay(zone);
        long seconds = Duration.between(clock.instant(), midnight.toInstant()).getSeconds();
        return Duration.ofSeconds(Math.max(1, seconds));
    }

    public enum Rejection {
        USER_MINUTE,
        USER_DAY,
        GLOBAL_DAY,
        USER_BUSY,
        GLOBAL_BUSY
    }

    public static final class Permit {

        private final UUID userId;
        private final AiUsageLimiter limiter;
        private final Rejection rejection;
        private final Duration retryAfter;
        private boolean released;

        private Permit(UUID userId, AiUsageLimiter limiter, Rejection rejection, Duration retryAfter) {
            this.userId = userId;
            this.limiter = limiter;
            this.rejection = rejection;
            this.retryAfter = retryAfter;
        }

        static Permit acquired(UUID userId, AiUsageLimiter limiter) {
            return new Permit(userId, limiter, null, null);
        }

        static Permit rejected(Rejection rejection, Duration retryAfter) {
            return new Permit(null, null, rejection, retryAfter);
        }

        public boolean allowed() {
            return rejection == null;
        }

        public Rejection rejection() {
            return rejection;
        }

        public Duration retryAfter() {
            return retryAfter;
        }

        public void release() {
            if (!allowed() || released) {
                return;
            }
            released = true;
            limiter.release(userId);
        }
    }

    private static final class UserCounters {

        private final Deque<Instant> minuteStamps = new ArrayDeque<>();
        private LocalDate day;
        private int dayCount;
        private int concurrent;

        private UserCounters(LocalDate day) {
            this.day = day;
        }

        private void ensureDay(LocalDate today) {
            if (day == null || !day.equals(today)) {
                day = today;
                dayCount = 0;
            }
        }

        private void pruneMinute(Instant now) {
            Instant cutoff = now.minus(MINUTE_WINDOW);
            while (!minuteStamps.isEmpty() && minuteStamps.peekFirst().isBefore(cutoff)) {
                minuteStamps.removeFirst();
            }
        }
    }
}
