package de.wasessenwir.user;

import de.wasessenwir.web.ApiException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;

public final class Passwords {

    public static final int MIN_LENGTH = 12;
    public static final int MAX_BYTES = 72;

    private Passwords() {}

    public static void check(String password) {
        if (password == null || password.length() < MIN_LENGTH) {
            throw invalid("Das Passwort muss mindestens 12 Zeichen haben.");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw invalid("Das Passwort ist zu lang.");
        }
        if (password.chars().anyMatch(Character::isISOControl)) {
            throw invalid("Das Passwort enthält ungültige Zeichen.");
        }
    }

    public static void confirm(String password, String confirmation) {
        check(password);
        if (confirmation == null || !password.equals(confirmation)) {
            throw invalid("Die Passwörter stimmen nicht überein.");
        }
    }

    private static ApiException invalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message, "VALIDATION");
    }
}
