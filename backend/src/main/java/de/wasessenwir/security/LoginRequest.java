package de.wasessenwir.security;

public record LoginRequest(String username, String password, Boolean rememberMe) {

    public LoginRequest(String username, String password) {
        this(username, password, Boolean.FALSE);
    }

    public boolean remember() {
        return Boolean.TRUE.equals(rememberMe);
    }
}
