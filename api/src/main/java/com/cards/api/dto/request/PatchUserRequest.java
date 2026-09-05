package com.cards.api.dto.request;

import com.cards.api.util.StringUtils;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.util.Locale;

public record PatchUserRequest(
    @Schema(description = "New username", example = "john_doe_updated", minLength = 4, maxLength = 20)
    @Size(min = 4, max = 20, message = "The username must be between 4 and 20 characters")
    String username,

    @Schema(description = "New email address", example = "john.new@example.com")
    @Email(message = "The email must be a valid email address")
    @Size(min = 1, max = 100, message = "The email must be at most 100 characters")
    String email,

    @Schema(description = "Session threshold in minutes (5-360)", example = "25", minimum = "5", maximum = "360")
    @Min(value = 5, message = "The session threshold must be at least 5 minutes")
    @Max(value = 360, message = "The session threshold must be at most 360 minutes")
    Integer sessionThreshold,

    @Schema(description = "Start of day hour (0-23)", example = "8", minimum = "0", maximum = "23")
    @Min(value = 0, message = "The start of day must be at least 0")
    @Max(value = 23, message = "The start of day must be at most 23")
    Integer startOfDay,

    @Schema(description = "Whether email notifications are enabled", example = "true")
    Boolean notificationsEnabled,

    @Schema(description = "Current password (required to set a new password)", example = "OldP@ss1!")
    String currentPassword,

    @Schema(description = "New password (must contain uppercase, lowercase, number, and special character)", example = "NewP@ss2!", minLength = 8, maxLength = 20)
    @Size(min = 8, max = 20, message = "The new password must be between 8 and 20 characters")
    @Pattern(
        regexp = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=!])(?=\\S+$).{8,20}$",
        message = "The new password must contain at least 1 uppercase letter, 1 lowercase letter, 1 number, and 1 special character"
    )
    String password
) {
    public PatchUserRequest {
        username = StringUtils.sanitizeString(username);
        email = (email == null) ? null : StringUtils.sanitizeString(email).toLowerCase(Locale.ROOT);
    }
}
