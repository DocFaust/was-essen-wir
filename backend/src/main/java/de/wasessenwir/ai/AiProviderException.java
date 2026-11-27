package de.wasessenwir.ai;

public class AiProviderException extends RuntimeException {

    public enum Kind {
        TIMEOUT,
        UNREACHABLE,
        INVALID,
        QUOTA,
        UNAUTHORIZED,
        RATE_LIMITED,
        PROVIDER
    }

    private final Kind kind;

    private AiProviderException(Kind kind) {
        super(kind.name());
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }

    public static AiProviderException timeout() {
        return new AiProviderException(Kind.TIMEOUT);
    }

    public static AiProviderException unreachable() {
        return new AiProviderException(Kind.UNREACHABLE);
    }

    public static AiProviderException invalid() {
        return new AiProviderException(Kind.INVALID);
    }

    public static AiProviderException quota() {
        return new AiProviderException(Kind.QUOTA);
    }

    public static AiProviderException unauthorized() {
        return new AiProviderException(Kind.UNAUTHORIZED);
    }

    public static AiProviderException rateLimited() {
        return new AiProviderException(Kind.RATE_LIMITED);
    }

    public static AiProviderException provider() {
        return new AiProviderException(Kind.PROVIDER);
    }

    public static AiProviderException forStatus(int status, String body) {
        String folded = body == null ? "" : body.toLowerCase(java.util.Locale.ROOT);
        if (folded.contains("insufficient_quota") || folded.contains("credit_balance_exhausted") || status == 402) {
            return quota();
        }
        return switch (status) {
            case 401, 403 -> unauthorized();
            case 408 -> timeout();
            case 429 -> rateLimited();
            default -> provider();
        };
    }
}
