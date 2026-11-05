package de.wasessenwir.security;

import java.time.OffsetDateTime;
import java.util.UUID;

record RememberMeToken(
        String selector,
        UUID userId,
        byte[] tokenHash,
        byte[] previousTokenHash,
        OffsetDateTime rotatedAt,
        OffsetDateTime expiresAt,
        OffsetDateTime createdAt) {}
