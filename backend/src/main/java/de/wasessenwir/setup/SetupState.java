package de.wasessenwir.setup;

import java.time.OffsetDateTime;

public record SetupState(
        byte[] tokenHash,
        OffsetDateTime expiresAt,
        OffsetDateTime completedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public boolean completed() {
        return completedAt != null;
    }
}
