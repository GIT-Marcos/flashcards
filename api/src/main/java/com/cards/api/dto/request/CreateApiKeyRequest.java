package com.cards.api.dto.request;

import com.cards.api.util.AiProvider;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateApiKeyRequest(
    @NotNull AiProvider provider,
    @NotBlank String apiKey
) {
    public CreateApiKeyRequest {
        if (apiKey != null) {
            apiKey = apiKey.strip();
        }
    }
}
