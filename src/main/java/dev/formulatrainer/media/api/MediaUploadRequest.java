package dev.formulatrainer.media.api;

import jakarta.validation.constraints.Size;

public record MediaUploadRequest(@Size(max = 500, message = "Alternative text must be at most 500 characters")
String altText) {
}
