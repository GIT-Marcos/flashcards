package com.cards.api.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;

public record AuthResponse(
    @Schema(description = "JWT access token (Bearer)", example = "eyJhbGciOiJIUzI1NiIs...")
    String accessToken,

    @JsonIgnore String refreshToken,

    @Schema(description = "Username of the authenticated user", example = "john_doe")
    String username
) {
}
