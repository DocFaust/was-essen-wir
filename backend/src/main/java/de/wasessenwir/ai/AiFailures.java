package de.wasessenwir.ai;

import de.wasessenwir.web.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

final class AiFailures {

    private static final Logger log = LoggerFactory.getLogger(AiFailures.class);

    private AiFailures() {
    }

    static ApiException toApi(AiProviderException exception) {
        log.warn("KI-Anfrage fehlgeschlagen: {}", exception.kind());
        return switch (exception.kind()) {
            case TIMEOUT -> new ApiException(
                    HttpStatus.GATEWAY_TIMEOUT,
                    "Der Essensassistent hat zu lange gebraucht. Bitte versuche es erneut.",
                    "AI_TIMEOUT");
            case UNREACHABLE -> new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Der Essensassistent ist gerade nicht erreichbar.",
                    "AI_UNAVAILABLE");
            case INVALID -> new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "Der Essensassistent konnte die Antwort nicht auswerten.",
                    "AI_INVALID");
            case QUOTA -> new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Das Guthaben beim KI-Anbieter ist aufgebraucht. Bitte eine administrierende Person informieren.",
                    "AI_QUOTA");
            case UNAUTHORIZED -> new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Der Zugang zum KI-Anbieter ist ungültig oder nicht berechtigt. Bitte eine administrierende Person informieren.",
                    "AI_PROVIDER_AUTH");
            case RATE_LIMITED -> new ApiException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Der KI-Anbieter begrenzt gerade die Anfragen. Bitte versuche es in kurzer Zeit erneut.",
                    "AI_PROVIDER_RATE_LIMIT");
            case PROVIDER -> new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "Der KI-Anbieter hat die Anfrage abgelehnt. Bitte versuche es später erneut.",
                    "AI_PROVIDER_ERROR");
        };
    }
}
