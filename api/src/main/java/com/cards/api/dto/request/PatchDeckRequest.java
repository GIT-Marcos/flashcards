package com.cards.api.dto.request;

import com.cards.api.util.StringUtils;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

public record PatchDeckRequest(
    @Schema(description = "New deck name", example = "Advanced Spanish Vocabulary", maxLength = 100)
    @Size(max = 100, message = "The deck name must be at most 100 characters")
    String name
) {
    public PatchDeckRequest {
        name = StringUtils.sanitizeString(name);
    }
}
