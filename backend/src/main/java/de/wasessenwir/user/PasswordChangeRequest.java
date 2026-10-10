package de.wasessenwir.user;

public record PasswordChangeRequest(String currentPassword, String newPassword, String passwordConfirmation) {}
