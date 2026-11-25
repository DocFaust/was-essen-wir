package de.wasessenwir.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class AttemptLimiterTest {

    private static final Duration WINDOW = Duration.ofMinutes(15);

    @Test
    void reachesAndExpiresALock() {
        MutableClock clock = new MutableClock(Instant.parse("2026-11-25T12:00:00Z"));
        AttemptLimiter limiter = AttemptLimiter.forTests(clock, 100);

        assertThat(limiter.tryAcquire("login:ada|127.0.0.1", 3, WINDOW)).isTrue();
        assertThat(limiter.tryAcquire("login:ada|127.0.0.1", 3, WINDOW)).isTrue();
        assertThat(limiter.tryAcquire("login:ada|127.0.0.1", 3, WINDOW)).isTrue();
        assertThat(limiter.tryAcquire("login:ada|127.0.0.1", 3, WINDOW)).isFalse();

        clock.advance(WINDOW.plusSeconds(1));
        assertThat(limiter.tryAcquire("login:ada|127.0.0.1", 3, WINDOW)).isTrue();
    }

    @Test
    void manyDistinctKeysStayWithinCapacity() {
        AttemptLimiter limiter = AttemptLimiter.forTests(Clock.systemUTC(), 50);

        int accepted = 0;
        for (int i = 0; i < 200; i++) {
            if (limiter.tryAcquire("login:user" + i + "|10.0.0.1", 8, WINDOW)) {
                accepted++;
            }
        }

        assertThat(limiter.size()).isLessThanOrEqualTo(50);
        assertThat(accepted).isEqualTo(50);
        assertThat(limiter.tryAcquire("login:overflow|10.0.0.1", 8, WINDOW)).isFalse();
    }

    @Test
    void capacityLimitDoesNotEvictActiveLocks() {
        MutableClock clock = new MutableClock(Instant.parse("2026-11-25T12:00:00Z"));
        AttemptLimiter limiter = AttemptLimiter.forTests(clock, 3);

        assertThat(limiter.tryAcquire("login:victim|1.1.1.1", 2, WINDOW)).isTrue();
        assertThat(limiter.tryAcquire("login:victim|1.1.1.1", 2, WINDOW)).isTrue();
        assertThat(limiter.tryAcquire("login:victim|1.1.1.1", 2, WINDOW)).isFalse();

        assertThat(limiter.tryAcquire("login:flood-a|2.2.2.2", 8, WINDOW)).isTrue();
        assertThat(limiter.tryAcquire("login:flood-b|2.2.2.2", 8, WINDOW)).isTrue();
        assertThat(limiter.tryAcquire("login:flood-c|2.2.2.2", 8, WINDOW)).isFalse();

        assertThat(limiter.tryAcquire("login:victim|1.1.1.1", 2, WINDOW)).isFalse();
        assertThat(limiter.size()).isEqualTo(3);
    }

    @Test
    void purgeRemovesExpiredEntriesAndFreesCapacity() {
        MutableClock clock = new MutableClock(Instant.parse("2026-11-25T12:00:00Z"));
        AttemptLimiter limiter = AttemptLimiter.forTests(clock, 2);

        assertThat(limiter.tryAcquire("login:old-a|1.1.1.1", 8, WINDOW)).isTrue();
        assertThat(limiter.tryAcquire("login:old-b|1.1.1.1", 8, WINDOW)).isTrue();
        assertThat(limiter.tryAcquire("login:new|1.1.1.1", 8, WINDOW)).isFalse();

        clock.advance(WINDOW.plusSeconds(1));
        limiter.purgeExpired();
        assertThat(limiter.size()).isZero();
        assertThat(limiter.tryAcquire("login:new|1.1.1.1", 8, WINDOW)).isTrue();
    }

    @Test
    void resetClearsAKey() {
        AttemptLimiter limiter = AttemptLimiter.forTests(Clock.systemUTC(), 10);
        assertThat(limiter.tryAcquire("password:id", 1, WINDOW)).isTrue();
        assertThat(limiter.tryAcquire("password:id", 1, WINDOW)).isFalse();
        limiter.reset("password:id");
        assertThat(limiter.tryAcquire("password:id", 1, WINDOW)).isTrue();
    }

    @Test
    void normalizesBlankAndOverlongKeys() {
        assertThat(AttemptLimiter.normalizeKey(null)).isEqualTo("unknown");
        assertThat(AttemptLimiter.normalizeKey("  ")).isEqualTo("unknown");
        String longKey = "x".repeat(AttemptLimiter.MAX_KEY_LENGTH + 40);
        String normalized = AttemptLimiter.normalizeKey(longKey);
        assertThat(normalized).hasSize(AttemptLimiter.MAX_KEY_LENGTH);

        AttemptLimiter limiter = AttemptLimiter.forTests(Clock.systemUTC(), 10);
        assertThat(limiter.tryAcquire(longKey, 1, WINDOW)).isTrue();
        assertThat(limiter.tryAcquire(normalized, 1, WINDOW)).isFalse();
        assertThat(limiter.size()).isEqualTo(1);
    }

    @Test
    void parallelAccessRemainsConsistent() throws Exception {
        AttemptLimiter limiter = AttemptLimiter.forTests(Clock.systemUTC(), 1_000);
        int threads = 32;
        int attemptsPerThread = 50;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger allowed = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            futures.add(pool.submit(() -> {
                start.await();
                for (int i = 0; i < attemptsPerThread; i++) {
                    if (limiter.tryAcquire("login:shared|127.0.0.1", 10, WINDOW)) {
                        allowed.incrementAndGet();
                    }
                }
                return null;
            }));
        }
        start.countDown();
        for (Future<?> future : futures) {
            future.get(10, TimeUnit.SECONDS);
        }
        pool.shutdownNow();
        assertThat(allowed.get()).isEqualTo(10);
        assertThat(limiter.tryAcquire("login:shared|127.0.0.1", 10, WINDOW)).isFalse();
    }

    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return Clock.fixed(instant, zone);
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
