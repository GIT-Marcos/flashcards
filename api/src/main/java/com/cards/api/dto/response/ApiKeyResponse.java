package com.cards.api.dto.response;

import com.cards.api.util.AiProvider;

import java.time.Instant;

public record ApiKeyResponse(
    Long id,
    AiProvider provider,
    String keyAlias,
    Instant createdAt
) {
}
