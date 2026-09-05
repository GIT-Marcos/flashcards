package com.cards.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ReviewRequest(
    @Schema(description = "Quality of recall (0 = complete blackout, 5 = perfect response)", example = "4", minimum = "0", maximum = "5")
    @NotNull(message = "The quality is required")
    @Min(value = 0, message = "The quality must be at least 0")
    @Max(value = 5, message = "The quality must be at most 5")
    Integer quality
) {
}
