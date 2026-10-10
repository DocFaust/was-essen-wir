package de.wasessenwir.user;

import java.time.OffsetDateTime;
import java.util.UUID;

public record StoredUser(
        UUID id,
        String username,
        String usernameNormalized,
        String displayName,
        String passwordHash,
        Role role,
        boolean active,
        boolean passwordChangeRequired,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {}
