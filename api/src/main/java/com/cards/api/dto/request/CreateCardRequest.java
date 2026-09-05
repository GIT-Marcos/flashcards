package com.cards.api.dto.request;

import com.cards.api.util.StringUtils;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCardRequest(
    @Schema(description = "Front side of the card (question/prompt)", example = "What does 'hola' mean?", maxLength = 255)
    @NotBlank(message = "The front is required")
    @Size(max = 255, message = "The front must be at most 255 characters")
    String front,

    @Schema(description = "Back side of the card (answer)", example = "Hello", maxLength = 5000)
    @NotBlank(message = "The back is required")
    @Size(max = 5000, message = "The back must be at most 5000 characters")
    String back
) {
    public CreateCardRequest {
        front = StringUtils.sanitizeString(front);
        back = StringUtils.sanitizeString(back);
    }
}
