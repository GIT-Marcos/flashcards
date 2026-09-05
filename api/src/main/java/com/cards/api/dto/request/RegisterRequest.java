package com.cards.api.dto.request;

import com.cards.api.util.StringUtils;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Locale;

public record RegisterRequest(
    @Schema(description = "Username", example = "john_doe", minLength = 4, maxLength = 50)
    @NotBlank(message = "The username is required")
    @Size(min = 4, max = 50, message = "The username must be between 4 and 50 characters")
    String username,

    @Schema(description = "Email address", example = "john@example.com")
    @NotBlank(message = "The email is required")
    @Email(message = "The email must be a valid email address")
    String email,

    @Schema(description = "Password (must contain uppercase, lowercase, number, and special character)", example = "P@ssw0rd!", minLength = 8, maxLength = 20)
    @NotBlank(message = "The password is required")
    @Size(min = 8, max = 20, message = "The password must be between 8 and 20 characters")
    @Pattern(
        regexp = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=!])(?=\\S+$).{8,20}$",
        message = "The password must contain at least 1 uppercase letter, 1 lowercase letter, 1 number, and 1 special character"
    )
    String password,

    @Schema(description = "IANA time zone identifier", example = "America/Argentina/Buenos_Aires", minLength = 3, maxLength = 50)
    @NotBlank(message = "The time zone is required")
    @Size(min = 3, max = 50, message = "The time zone must be between 3 and 50 characters")
    String zoneInfo
) {
    public RegisterRequest {
        username = StringUtils.sanitizeString(username);
        email = StringUtils.sanitizeString(email).toLowerCase(Locale.ROOT);
    }
}
