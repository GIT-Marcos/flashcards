package com.cards.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "Request to reset password with token")
public record ResetPasswordRequest(
    @Schema(description = "Password reset token (received via email)")
    @NotBlank(message = "The reset token is required")
    String token,

    @Schema(
        description = "New password (must contain uppercase, lowercase, number, and special character)",
        example = "NewP@ssw0rd!",
        minLength = 8,
        maxLength = 20
    )
    @NotBlank(message = "The new password is required")
    @Size(min = 8, max = 20, message = "The password must be between 8 and 20 characters")
    @Pattern(
        regexp = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=!])(?=\\S+$).{8,20}$",
        message = "The password must contain at least 1 uppercase letter, 1 lowercase letter, 1 number, and 1 special character"
    )
    String newPassword
) {
}
