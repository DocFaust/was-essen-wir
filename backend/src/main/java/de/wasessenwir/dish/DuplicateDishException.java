package de.wasessenwir.dish;

public class DuplicateDishException extends RuntimeException {

    public DuplicateDishException(String message) {
        super(message);
    }
}
