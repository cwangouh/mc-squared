package dev.formulatrainer.card.api;

import dev.formulatrainer.media.api.MediaResponse;
import java.time.Instant;
import java.util.UUID;

public record CardResponse(UUID id, String frontContent, String backContent, MediaResponse frontImage,
    MediaResponse backImage, Instant createdAt, Instant updatedAt) {
}
