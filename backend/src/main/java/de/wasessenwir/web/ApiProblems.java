package de.wasessenwir.web;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

public final class ApiProblems {

    private ApiProblems() {}

    public static ProblemDetail of(HttpStatus status, String title, String detail, String code) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        if (code != null) {
            problem.setProperty("code", code);
        }
        return problem;
    }

    public static void write(HttpServletResponse response, ProblemDetail problem) throws IOException {
        response.setStatus(problem.getStatus());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/problem+json");
        Object code = problem.getProperties() == null ? null : problem.getProperties().get("code");
        StringBuilder json = new StringBuilder();
        json.append("{\"title\":\"").append(escape(problem.getTitle())).append('"');
        json.append(",\"status\":").append(problem.getStatus());
        json.append(",\"detail\":\"").append(escape(problem.getDetail())).append('"');
        if (code != null) {
            json.append(",\"code\":\"").append(escape(String.valueOf(code))).append('"');
        }
        json.append('}');
        response.getWriter().write(json.toString());
    }

    public static String title(HttpStatus status) {
        return switch (status) {
            case BAD_REQUEST -> "Ungültige Eingabe";
            case UNAUTHORIZED -> "Nicht angemeldet";
            case FORBIDDEN -> "Keine Berechtigung";
            case NOT_FOUND -> "Nicht gefunden";
            case CONFLICT -> "Konflikt";
            case TOO_MANY_REQUESTS -> "Zu viele Versuche";
            default -> "Anfrage abgelehnt";
        };
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
    }
}
