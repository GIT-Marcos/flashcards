package com.cards.api.dto.request;

import com.cards.api.util.StringUtils;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

public record PatchCardRequest(
    @Schema(description = "New front side text", example = "What does 'adiós' mean?", minLength = 1, maxLength = 255)
    @Size(min = 1, max = 255, message = "The front must be between 1 and 255 characters")
    String front,

    @Schema(description = "New back side text", example = "Goodbye", minLength = 1, maxLength = 5000)
    @Size(min = 1, max = 5000, message = "The back must be between 1 and 5000 characters")
    String back
) {
    public PatchCardRequest {
        front = StringUtils.sanitizeString(front);
        back = StringUtils.sanitizeString(back);
    }
}
