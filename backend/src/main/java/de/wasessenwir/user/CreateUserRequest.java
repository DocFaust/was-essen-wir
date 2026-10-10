package de.wasessenwir.user;

public record CreateUserRequest(String username, String displayName, String password, String role) {}
