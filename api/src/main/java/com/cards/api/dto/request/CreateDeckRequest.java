package com.cards.api.dto.request;

import com.cards.api.util.StringUtils;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateDeckRequest(
    @Schema(description = "Deck name", example = "Spanish Vocabulary", maxLength = 100)
    @NotBlank(message = "The deck name is required")
    @Size(max = 100, message = "The deck name must be at most 100 characters")
    String name
) {
    public CreateDeckRequest {
        name = StringUtils.sanitizeString(name);
    }
}
