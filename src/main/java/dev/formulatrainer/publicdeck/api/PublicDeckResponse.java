package dev.formulatrainer.publicdeck.api;

import dev.formulatrainer.card.api.CardResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PublicDeckResponse(UUID publicId, String title, String description, long cardCount,
    List<CardResponse> cards, Instant createdAt, Instant updatedAt) {
}
