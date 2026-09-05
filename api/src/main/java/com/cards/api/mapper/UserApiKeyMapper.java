package com.cards.api.mapper;

import com.cards.api.dto.request.CreateApiKeyRequest;
import com.cards.api.dto.response.ApiKeyResponse;
import com.cards.api.entity.User;
import com.cards.api.entity.UserApiKey;
import org.springframework.stereotype.Component;

@Component
public class UserApiKeyMapper {

    public UserApiKey toEntity(User user, CreateApiKeyRequest request) {
        return UserApiKey.builder()
            .user(user)
            .provider(request.provider())
            .keyAlias(buildKeyAlias(request.apiKey()))
            .encryptedKey(request.apiKey())
            .build();
    }

    public ApiKeyResponse toResponse(UserApiKey entity) {
        return new ApiKeyResponse(
            entity.getId(),
            entity.getProvider(),
            entity.getKeyAlias(),
            entity.getCreatedAt()
        );
    }

    private static String buildKeyAlias(String apiKey) {
        if (apiKey == null || apiKey.length() < 4) {
            return apiKey;
        }
        return "..." + apiKey.substring(apiKey.length() - 4);
    }
}
