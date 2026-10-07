package dev.formulatrainer.media.api;

import java.util.UUID;

public record MediaResponse(UUID id, String url, String altText) {
}
