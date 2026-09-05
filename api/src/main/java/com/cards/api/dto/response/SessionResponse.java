package com.cards.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

public record SessionResponse(
    @Schema(description = "Session ID", example = "1")
    Long id,

    @Schema(description = "Session start time (ISO-8601)", example = "2026-05-19T10:00:00Z")
    Instant startTime,

    @Schema(description = "Session end time (ISO-8601)", example = "2026-05-19T10:30:00Z")
    Instant endTime,

    @Schema(description = "Number of cards reviewed", example = "20")
    Integer cardsReviewed,

    @Schema(description = "Accuracy rate (0.0 - 1.0)", example = "0.85")
    Double accuracyRate,

    @Schema(description = "Session duration in seconds", example = "1800")
    Long durationSeconds
) {
}
