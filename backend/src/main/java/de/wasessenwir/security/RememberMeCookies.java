package de.wasessenwir.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;

final class RememberMeCookies {

    static final String NAME = "WAEW_REMEMBER";

    private RememberMeCookies() {}

    static void write(HttpServletResponse response, String value, Duration maxAge, boolean secure) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(value, maxAge, secure).toString());
    }

    static void clear(HttpServletResponse response, boolean secure) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO, secure).toString());
    }

    static String read(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (NAME.equals(cookie.getName()) && cookie.getValue() != null && !cookie.getValue().isBlank()) {
                return cookie.getValue();
            }
        }
        return null;
    }

    static Parsed parse(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        int dot = value.indexOf('.');
        if (dot <= 0 || dot != value.lastIndexOf('.')) {
            return null;
        }
        String selector = value.substring(0, dot);
        String secret = value.substring(dot + 1);
        if (!tokenPart(selector, 20, 32) || !tokenPart(secret, 40, 64)) {
            return null;
        }
        return new Parsed(selector, secret);
    }

    private static boolean tokenPart(String value, int min, int max) {
        int length = value.length();
        if (length < min || length > max) {
            return false;
        }
        for (int index = 0; index < length; index++) {
            char character = value.charAt(index);
            boolean allowed = (character >= 'A' && character <= 'Z')
                    || (character >= 'a' && character <= 'z')
                    || (character >= '0' && character <= '9')
                    || character == '-'
                    || character == '_';
            if (!allowed) {
                return false;
            }
        }
        return true;
    }

    private static ResponseCookie cookie(String value, Duration maxAge, boolean secure) {
        return ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path("/")
                .maxAge(maxAge)
                .build();
    }

    record Parsed(String selector, String secret) {}
}
