package de.wasessenwir.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class AiUsageLimiterTest {

    private static final UUID ADA = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID BEA = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

    @Test
    void enforcesPerUserMinuteLimitAndExpires() {
        MutableClock clock = new MutableClock(Instant.parse("2026-11-25T12:00:00Z"), BERLIN);
        AiUsageLimiter limiter = AiUsageLimiter.forTests(clock, limits(2, 50, 150, 5, 5, 100));

        acquireAndRelease(limiter, ADA);
        acquireAndRelease(limiter, ADA);
        AiUsageLimiter.Permit denied = limiter.tryAcquire(ADA);
        assertThat(denied.allowed()).isFalse();
        assertThat(denied.rejection()).isEqualTo(AiUsageLimiter.Rejection.USER_MINUTE);
        assertThat(denied.retryAfter().toSeconds()).isGreaterThan(0);

        clock.advance(Duration.ofMinutes(1).plusSeconds(1));
        assertThat(limiter.tryAcquire(ADA).allowed()).isTrue();
    }

    @Test
    void enforcesPerUserAndGlobalDayLimitsAcrossDayChange() {
        MutableClock clock = new MutableClock(Instant.parse("2026-11-25T22:30:00Z"), BERLIN);
        AiUsageLimiter limiter = AiUsageLimiter.forTests(clock, limits(50, 2, 3, 5, 5, 100));

        acquireAndRelease(limiter, ADA);
        acquireAndRelease(limiter, ADA);
        assertThat(limiter.tryAcquire(ADA).rejection()).isEqualTo(AiUsageLimiter.Rejection.USER_DAY);

        acquireAndRelease(limiter, BEA);
        assertThat(limiter.tryAcquire(BEA).rejection()).isEqualTo(AiUsageLimiter.Rejection.GLOBAL_DAY);

        clock.advance(Duration.ofHours(2));
        assertThat(limiter.tryAcquire(ADA).allowed()).isTrue();
        assertThat(limiter.tryAcquire(BEA).allowed()).isTrue();
    }

    @Test
    void keepsUserLimitsIndependent() {
        AiUsageLimiter limiter = AiUsageLimiter.forTests(
                Clock.fixed(Instant.parse("2026-11-25T12:00:00Z"), BERLIN),
                limits(1, 50, 150, 5, 5, 100));

        acquireAndRelease(limiter, ADA);
        assertThat(limiter.tryAcquire(ADA).allowed()).isFalse();
        assertThat(limiter.tryAcquire(BEA).allowed()).isTrue();
    }

    @Test
    void enforcesConcurrencyAndReleasesCapacity() {
        AiUsageLimiter limiter = AiUsageLimiter.forTests(
                Clock.fixed(Instant.parse("2026-11-25T12:00:00Z"), BERLIN),
                limits(50, 50, 150, 1, 2, 100));

        AiUsageLimiter.Permit first = limiter.tryAcquire(ADA);
        assertThat(first.allowed()).isTrue();
        assertThat(limiter.tryAcquire(ADA).rejection()).isEqualTo(AiUsageLimiter.Rejection.USER_BUSY);

        AiUsageLimiter.Permit secondUser = limiter.tryAcquire(BEA);
        assertThat(secondUser.allowed()).isTrue();
        assertThat(limiter.tryAcquire(UUID.randomUUID()).rejection()).isEqualTo(AiUsageLimiter.Rejection.GLOBAL_BUSY);

        first.release();
        assertThat(limiter.userConcurrent(ADA)).isZero();
        assertThat(limiter.tryAcquire(ADA).allowed()).isTrue();

        secondUser.release();
        assertThat(limiter.globalConcurrent()).isEqualTo(1);
    }

    @Test
    void rejectedRequestsDoNotConsumeQuotaOrCapacity() {
        AiUsageLimiter limiter = AiUsageLimiter.forTests(
                Clock.fixed(Instant.parse("2026-11-25T12:00:00Z"), BERLIN),
                limits(1, 50, 150, 1, 1, 100));

        AiUsageLimiter.Permit held = limiter.tryAcquire(ADA);
        assertThat(held.allowed()).isTrue();
        assertThat(limiter.tryAcquire(BEA).allowed()).isFalse();
        held.release();

        assertThat(limiter.globalConcurrent()).isZero();
        assertThat(limiter.tryAcquire(BEA).allowed()).isTrue();
    }

    @Test
    void purgesIdleUsersWhenCapacityIsReached() {
        MutableClock clock = new MutableClock(Instant.parse("2026-11-25T22:30:00Z"), BERLIN);
        AiUsageLimiter limiter = AiUsageLimiter.forTests(clock, limits(50, 50, 150, 2, 3, 2));

        assertThat(limiter.tryAcquire(ADA).allowed()).isTrue();
        limiter.release(ADA);
        assertThat(limiter.tryAcquire(BEA).allowed()).isTrue();
        limiter.release(BEA);
        assertThat(limiter.trackedUsers()).isEqualTo(2);

        clock.advance(Duration.ofHours(2));
        UUID chris = UUID.fromString("33333333-3333-3333-3333-333333333333");
        assertThat(limiter.tryAcquire(chris).allowed()).isTrue();
        assertThat(limiter.trackedUsers()).isLessThanOrEqualTo(2);
    }

    @Test
    void parallelAcquiresDoNotExceedLimits() throws Exception {
        AiUsageLimiter limiter = AiUsageLimiter.forTests(
                Clock.fixed(Instant.parse("2026-11-25T12:00:00Z"), BERLIN),
                limits(10, 50, 150, 5, 5, 100));
        int threads = 32;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger allowed = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                AiUsageLimiter.Permit permit = limiter.tryAcquire(ADA);
                if (permit.allowed()) {
                    allowed.incrementAndGet();
                    permit.release();
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
        assertThat(limiter.globalConcurrent()).isZero();
    }

    private static void acquireAndRelease(AiUsageLimiter limiter, UUID userId) {
        AiUsageLimiter.Permit permit = limiter.tryAcquire(userId);
        assertThat(permit.allowed()).isTrue();
        permit.release();
    }

    private static AiProperties.RateLimit limits(
            int perMinute, int perDay, int globalDay, int concurrentUser, int concurrentGlobal, int maxUsers) {
        AiProperties.RateLimit limits = new AiProperties.RateLimit();
        limits.setPerUserPerMinute(perMinute);
        limits.setPerUserPerDay(perDay);
        limits.setGlobalPerDay(globalDay);
        limits.setMaxConcurrentPerUser(concurrentUser);
        limits.setMaxConcurrentGlobal(concurrentGlobal);
        limits.setMaxTrackedUsers(maxUsers);
        return limits;
    }

    private static final class MutableClock extends Clock {

        private Instant instant;
        private final ZoneId zone;

        private MutableClock(Instant instant, ZoneId zone) {
            this.instant = instant;
            this.zone = zone;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return new MutableClock(instant, zone);
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
