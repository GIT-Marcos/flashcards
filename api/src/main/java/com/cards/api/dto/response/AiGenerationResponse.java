package com.cards.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Result of AI-powered card generation")
public record AiGenerationResponse(

    @Schema(description = "The deck (existing or newly created)")
    DeckResponse deck,

    @Schema(description = "Generated cards")
    List<CardResponse> cards,

    @Schema(description = "Number of cards successfully created", example = "10")
    int totalGenerated,

    @Schema(description = "Number of cards skipped due to duplicates or validation", example = "2")
    int totalSkipped
) {
}
