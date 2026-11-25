package de.wasessenwir.web;

import java.time.Duration;
import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final Duration retryAfter;

    public ApiException(HttpStatus status, String message, String code) {
        this(status, message, code, null);
    }

    public ApiException(HttpStatus status, String message, String code, Duration retryAfter) {
        super(message);
        this.status = status;
        this.code = code;
        this.retryAfter = retryAfter;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }

    public Duration retryAfter() {
        return retryAfter;
    }
}
