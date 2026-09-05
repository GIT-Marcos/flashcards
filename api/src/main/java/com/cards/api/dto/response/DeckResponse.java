package com.cards.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

public record DeckResponse(
    @Schema(description = "Deck ID", example = "1")
    Long id,

    @Schema(description = "Deck name", example = "Spanish Vocabulary")
    String name,

    @Schema(description = "Whether the deck has cards pending review", example = "true")
    boolean hasPendingCards,

    @Schema(description = "Deck creation timestamp (ISO-8601)", example = "2026-01-15T10:30:00Z")
    Instant createdAt,

    @Schema(description = "Deck last update timestamp (ISO-8601)", example = "2026-05-19T14:00:00Z")
    Instant updatedAt
) {
}
