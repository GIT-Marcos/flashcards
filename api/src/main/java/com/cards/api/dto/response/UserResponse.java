package com.cards.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.Set;

public record UserResponse(
    @Schema(description = "User ID", example = "1")
    Long id,

    @Schema(description = "Username", example = "john_doe")
    String username,

    @Schema(description = "Email address", example = "john@example.com")
    String email,

    @Schema(description = "IANA time zone identifier", example = "America/Argentina/Buenos_Aires")
    String zone,

    @Schema(description = "Account creation timestamp (ISO-8601)", example = "2026-01-15T10:30:00Z")
    Instant createdAt,

    @Schema(description = "Account last login timestamp (ISO-8601)", example = "2026-01-15T10:30:00Z")
    Instant lastLogin,

    @Schema(description = "Account last notification timestamp (ISO-8601)", example = "2026-01-15T10:30:00Z")
    Instant lastNotificationSent,

    @Schema(description = "Session threshold in minutes", example = "25")
    int sessionThreshold,

    @Schema(description = "Start of day hour (0-23)", example = "8")
    int startOfDay,

    @Schema(description = "Whether email notifications are enabled", example = "true")
    boolean notificationsEnabled,

    @Schema(description = "User roles", example = "[\"ROLE_USER\"]")
    Set<String> roles
) {
}
