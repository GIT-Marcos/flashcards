package com.cards.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record LoginRequest(
    @Schema(description = "Username", example = "john_doe", minLength = 4, maxLength = 50)
    @NotBlank(message = "The username is required")
    @Size(min = 4, max = 50, message = "The username must be between 4 and 50 characters")
    String username,

    @Schema(description = "Password (must contain uppercase, lowercase, number, and special character)", example = "P@ssw0rd!", minLength = 8, maxLength = 20)
    @NotBlank(message = "The password is required")
    @Size(min = 8, max = 20, message = "The password must be between 8 and 20 characters")
    @Pattern(
        regexp = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=!])(?=\\S+$).{8,20}$",
        message = "The password must contain at least 1 uppercase letter, 1 lowercase letter, 1 number, and 1 special character"
    )
    String password
) {
}
