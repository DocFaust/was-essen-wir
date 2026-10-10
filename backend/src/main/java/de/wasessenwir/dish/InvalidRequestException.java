package de.wasessenwir.dish;

public class InvalidRequestException extends RuntimeException {

    private final String code;

    public InvalidRequestException(String message) {
        this(message, null);
    }

    public InvalidRequestException(String message, String code) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
