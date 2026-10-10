package de.wasessenwir.user;

import java.time.OffsetDateTime;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String username,
        String displayName,
        Role role,
        boolean active,
        boolean passwordChangeRequired,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public static UserResponse from(StoredUser user) {
        return new UserResponse(
                user.id(),
                user.username(),
                user.displayName(),
                user.role(),
                user.active(),
                user.passwordChangeRequired(),
                user.createdAt(),
                user.updatedAt());
    }
}
