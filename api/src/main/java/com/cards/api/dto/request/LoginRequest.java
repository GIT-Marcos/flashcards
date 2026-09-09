package com.cards.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @Schema(description = "Username", example = "john_doe", minLength = 4, maxLength = 50)
        @NotBlank(message = "The username is required")
        @Size(min = 4, max = 50, message = "The username must be between 4 and 50 characters")
        String username,

        @Schema(description = "Password", example = "P@ssw0rd!")
        @NotBlank(message = "The password is required")
        String password
) {
}
