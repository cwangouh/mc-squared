package dev.formulatrainer.deck.api;

import java.time.Instant;
import java.util.UUID;

public record DeckResponse(UUID id, UUID publicId, String title, String description, long cardCount, String publicPath,
    Instant createdAt, Instant updatedAt) {
}
