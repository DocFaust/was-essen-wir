package de.wasessenwir.ai;

import org.springframework.test.context.DynamicPropertyRegistry;

final class AiTestSupport {

    private AiTestSupport() {}

    /** Hebt die produktiven Nutzungslimits für fachliche KI-Tests an, die viele Aufrufe machen. */
    static void disableUsageLimits(DynamicPropertyRegistry registry) {
        registry.add("app.ai.rate-limit.per-user-per-minute", () -> "1000");
        registry.add("app.ai.rate-limit.per-user-per-day", () -> "1000");
        registry.add("app.ai.rate-limit.global-per-day", () -> "1000");
        registry.add("app.ai.rate-limit.max-concurrent-per-user", () -> "10");
        registry.add("app.ai.rate-limit.max-concurrent-global", () -> "20");
    }
}
