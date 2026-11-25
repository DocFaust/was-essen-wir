package de.wasessenwir.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.wasessenwir.web.ApiException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class AiProviderGatewayTest {

    private static final UUID ADA = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Test
    void countsStartedCallsEvenWhenProviderFailsAndReleasesCapacity() {
        CountingProvider provider = new CountingProvider();
        provider.failWith = AiProviderException.timeout();
        AiUsageLimiter limiter = limiter(5, 50, 150, 1, 3);
        AiProviderGateway gateway = new AiProviderGateway(provider, limiter);

        assertThatThrownBy(() -> gateway.interpret(ADA, "system", "user"))
                .isInstanceOf(AiProviderException.class);
        assertThat(provider.calls.get()).isEqualTo(1);
        assertThat(limiter.globalConcurrent()).isZero();
        assertThat(limiter.userConcurrent(ADA)).isZero();

        provider.failWith = null;
        assertThat(gateway.interpret(ADA, "system", "ok")).isEqualTo("ok");
        assertThat(provider.calls.get()).isEqualTo(2);
    }

    @Test
    void doesNotCallProviderWhenLimitIsExceeded() {
        CountingProvider provider = new CountingProvider();
        AiUsageLimiter limiter = limiter(1, 50, 150, 1, 1);
        AiProviderGateway gateway = new AiProviderGateway(provider, limiter);

        assertThat(gateway.interpret(ADA, "system", "first")).isEqualTo("first");
        assertThatThrownBy(() -> gateway.interpret(ADA, "system", "second"))
                .isInstanceOf(ApiException.class)
                .satisfies(exception -> {
                    ApiException api = (ApiException) exception;
                    assertThat(api.status()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                    assertThat(api.code()).isEqualTo("AI_RATE_LIMITED");
                    assertThat(api.retryAfter()).isNotNull();
                });
        assertThat(provider.calls.get()).isEqualTo(1);
    }

    @Test
    void releasesCapacityAfterBusyWaitersAreRejected() throws Exception {
        CountingProvider provider = new CountingProvider();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        provider.block = () -> {
            started.countDown();
            assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
        };
        AiUsageLimiter limiter = limiter(50, 50, 150, 1, 1);
        AiProviderGateway gateway = new AiProviderGateway(provider, limiter);

        Thread worker = new Thread(() -> gateway.interpret(ADA, "system", "held"));
        worker.start();
        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();

        assertThatThrownBy(() -> gateway.draft(ADA, "system", "queued"))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).code())
                .isEqualTo("AI_BUSY");
        assertThat(provider.calls.get()).isEqualTo(1);

        release.countDown();
        worker.join(5_000);
        assertThat(limiter.globalConcurrent()).isZero();
        assertThat(gateway.draft(ADA, "system", "after")).isEqualTo("after");
    }

    private static AiUsageLimiter limiter(
            int perMinute, int perDay, int globalDay, int concurrentUser, int concurrentGlobal) {
        AiProperties.RateLimit limits = new AiProperties.RateLimit();
        limits.setPerUserPerMinute(perMinute);
        limits.setPerUserPerDay(perDay);
        limits.setGlobalPerDay(globalDay);
        limits.setMaxConcurrentPerUser(concurrentUser);
        limits.setMaxConcurrentGlobal(concurrentGlobal);
        limits.setMaxTrackedUsers(100);
        return AiUsageLimiter.forTests(Clock.fixed(Instant.parse("2026-11-25T12:00:00Z"), ZoneId.of("Europe/Berlin")), limits);
    }

    private static final class CountingProvider implements AiProvider {

        private final AtomicInteger calls = new AtomicInteger();
        private volatile AiProviderException failWith;
        private volatile Block block;

        @Override
        public String interpret(String systemPrompt, String userMessage) {
            return run(userMessage);
        }

        @Override
        public String draft(String systemPrompt, String userMessage) {
            return run(userMessage);
        }

        private String run(String userMessage) {
            calls.incrementAndGet();
            Block current = block;
            if (current != null) {
                try {
                    current.run();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw AiProviderException.unreachable();
                }
            }
            if (failWith != null) {
                throw failWith;
            }
            return userMessage;
        }
    }

    @FunctionalInterface
    private interface Block {
        void run() throws InterruptedException;
    }
}
