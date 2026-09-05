package com.cards.api.dto.request;

import com.cards.api.util.AiProvider;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Request to generate flashcards from a topic prompt using AI")
public record AiTopicRequest(
    @Schema(description = "Topic to generate flashcards about", example = "Spanish vocabulary for beginners", maxLength = 2000)
    @NotBlank(message = "The prompt is required")
    @Size(max = 2000, message = "The prompt must be at most 2000 characters")
    String prompt,

    @Schema(description = "AI provider to use", example = "OPENAI")
    @NotNull(message = "The provider is required")
    AiProvider provider,

    @Schema(description = "Name for the new deck", example = "Spanish Basics", maxLength = 100)
    @NotBlank(message = "The deck name is required")
    @Size(max = 100, message = "The deck name must be at most 100 characters")
    String deckName,

    @Schema(description = "Model override (required for OpenRouter, optional for others)", example = "gpt-4o-mini")
    String model
) {
}
