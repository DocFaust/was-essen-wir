package de.wasessenwir.setup;

public record SetupRequest(
        String username,
        String displayName,
        String password,
        String passwordConfirmation,
        String setupToken) {}
