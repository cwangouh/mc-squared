package dev.formulatrainer.card.api;

import jakarta.validation.constraints.Size;

public record CardSaveRequest(@Size(max = 20000, message = "Front content must be at most 20000 characters")
String frontContent,

    @Size(max = 20000, message = "Back content must be at most 20000 characters")
    String backContent) {
}
