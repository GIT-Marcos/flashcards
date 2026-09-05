package com.cards.api.dto.request;

import com.cards.api.util.StringUtils;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import java.util.Locale;

@Schema(description = "Request to initiate password reset")
public record ForgotPasswordRequest(
    @Schema(description = "Email address of the account", example = "john@example.com")
    @NotBlank(message = "The email is required")
    @Email(message = "The email must be a valid email address")
    String email
) {
    public ForgotPasswordRequest {
        email = StringUtils.sanitizeString(email).toLowerCase(Locale.ROOT);
    }
}
