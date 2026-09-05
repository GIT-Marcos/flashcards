package com.cards.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

public record CardResponse(
    @Schema(description = "Card ID", example = "1")
    Long id,

    @Schema(description = "Front side text (question/prompt)", example = "What does 'hola' mean?")
    String front,

    @Schema(description = "Back side text (answer)", example = "Hello")
    String back,

    @Schema(description = "Next review date (ISO-8601)", example = "2026-05-21T10:30:00Z")
    Instant nextReviewDate
) {
}
