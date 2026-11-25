package de.wasessenwir.ai;

import de.wasessenwir.web.ApiException;
import java.time.Duration;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Einziger Einstieg für produktive Provider-Aufrufe. Prüft Nutzungslimits vor dem Aufruf und gibt
 * Parallelitätskapazität danach wieder frei – auch bei Fehler, Timeout oder Abbruch.
 */
@Service
public class AiProviderGateway {

    private final AiProvider provider;
    private final AiUsageLimiter limiter;

    public AiProviderGateway(AiProvider provider, AiUsageLimiter limiter) {
        this.provider = provider;
        this.limiter = limiter;
    }

    public String interpret(UUID userId, String systemPrompt, String userMessage) {
        return execute(userId, () -> provider.interpret(systemPrompt, userMessage));
    }

    public String draft(UUID userId, String systemPrompt, String userMessage) {
        return execute(userId, () -> provider.draft(systemPrompt, userMessage));
    }

    private String execute(UUID userId, Supplier<String> call) {
        AiUsageLimiter.Permit permit = limiter.tryAcquire(userId);
        if (!permit.allowed()) {
            throw toException(permit);
        }
        try {
            return call.get();
        } finally {
            permit.release();
        }
    }

    static ApiException toException(AiUsageLimiter.Permit permit) {
        Duration retryAfter = permit.retryAfter() == null ? Duration.ofSeconds(1) : permit.retryAfter();
        return switch (permit.rejection()) {
            case USER_MINUTE -> new ApiException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Du hast den Essensassistenten zu oft genutzt. Bitte warte einen Moment.",
                    "AI_RATE_LIMITED",
                    retryAfter);
            case USER_BUSY, GLOBAL_BUSY -> new ApiException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Der Essensassistent ist gerade ausgelastet. Bitte versuche es in einem Moment erneut.",
                    "AI_BUSY",
                    retryAfter);
            case USER_DAY, GLOBAL_DAY -> new ApiException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Das Tageslimit für den Essensassistenten ist erreicht. Bitte versuche es morgen erneut.",
                    "AI_DAILY_LIMIT",
                    retryAfter);
        };
    }
}
