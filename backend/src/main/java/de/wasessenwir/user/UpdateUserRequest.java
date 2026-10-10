package de.wasessenwir.user;

public record UpdateUserRequest(String displayName, String role, Boolean active) {}
