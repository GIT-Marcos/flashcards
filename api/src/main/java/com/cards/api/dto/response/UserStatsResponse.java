package com.cards.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

public record UserStatsResponse(
    @Schema(description = "Total number of reviews submitted", example = "450")
    Long totalReviews,

    @Schema(description = "Global accuracy rate (0.0 - 1.0)", example = "0.82")
    Double globalAccuracyRate,

    @Schema(description = "Total number of study sessions", example = "25")
    Long totalSessions,

    @Schema(description = "Total number of cards reviewed across all sessions", example = "420")
    Long totalCardsReviewed,

    @Schema(description = "Quality distribution map (quality -> count)", example = "{\"0\": 10, \"1\": 15, \"2\": 30, \"3\": 80, \"4\": 150, \"5\": 165}")
    Map<Integer, Long> qualityDistribution
) {
}
