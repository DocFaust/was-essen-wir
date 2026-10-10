package de.wasessenwir.security;

import de.wasessenwir.web.ApiProblems;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.stereotype.Component;

@Component
public class ApiAuthenticationHandlers {

    public void unauthenticated(HttpServletRequest request, HttpServletResponse response, Exception exception)
            throws IOException {
        ApiProblems.write(
                response,
                ApiProblems.of(HttpStatus.UNAUTHORIZED, "Nicht angemeldet", "Bitte melde dich an.", "UNAUTHENTICATED"));
    }

    public void denied(HttpServletRequest request, HttpServletResponse response, AccessDeniedException exception)
            throws IOException {
        if (exception instanceof CsrfException) {
            ApiProblems.write(
                    response,
                    ApiProblems.of(
                            HttpStatus.FORBIDDEN,
                            "Anfrage abgelehnt",
                            "Die Anfrage konnte nicht bestätigt werden. Lade die Seite neu und versuche es erneut.",
                            "CSRF"));
            return;
        }
        ApiProblems.write(
                response,
                ApiProblems.of(HttpStatus.FORBIDDEN, "Keine Berechtigung", "Dafür fehlt die Berechtigung.", "FORBIDDEN"));
    }
}
