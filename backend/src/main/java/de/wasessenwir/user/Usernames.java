package de.wasessenwir.user;

import de.wasessenwir.web.ApiException;
import java.text.Normalizer;
import java.util.Locale;
import org.springframework.http.HttpStatus;

public final class Usernames {

    private static final String PATTERN = "\\p{L}[\\p{L}\\p{N}._-]{1,39}";

    private Usernames() {}

    public static String canonicalize(String raw) {
        if (raw == null) {
            throw invalid("Der Benutzername ist erforderlich.");
        }
        String canonical = Normalizer.normalize(raw.trim(), Normalizer.Form.NFKC);
        if (canonical.length() < 2 || canonical.length() > 40) {
            throw invalid("Der Benutzername muss 2 bis 40 Zeichen haben.");
        }
        if (!canonical.matches(PATTERN)) {
            throw invalid(
                    "Der Benutzername muss mit einem Buchstaben beginnen und darf nur Buchstaben, Ziffern, Punkt, Unterstrich und Bindestrich enthalten.");
        }
        return canonical;
    }

    public static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        return Normalizer.normalize(raw.trim(), Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
    }

    public static String displayName(String raw) {
        if (raw == null) {
            throw invalid("Der Anzeigename ist erforderlich.");
        }
        String name = raw.trim();
        if (name.isEmpty() || name.length() > 80) {
            throw invalid("Der Anzeigename muss 1 bis 80 Zeichen haben.");
        }
        if (name.chars().anyMatch(Character::isISOControl)) {
            throw invalid("Der Anzeigename enthält ungültige Zeichen.");
        }
        return name;
    }

    private static ApiException invalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message, "VALIDATION");
    }
}
