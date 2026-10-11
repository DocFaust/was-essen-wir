package de.wasessenwir.ai;

public class AiProviderException extends RuntimeException {

    public enum Kind {
        TIMEOUT,
        UNREACHABLE,
        INVALID
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
}
