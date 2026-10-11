package de.wasessenwir.ai;

import java.util.UUID;

public record DishDraftRequest(String description, DraftKind kind, UUID baseDishId) {
}
