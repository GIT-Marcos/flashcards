package com.cards.api.unit;

import com.cards.api.dto.request.CreateApiKeyRequest;
import com.cards.api.dto.response.ApiKeyResponse;
import com.cards.api.entity.User;
import com.cards.api.entity.UserApiKey;
import com.cards.api.mapper.UserApiKeyMapper;
import com.cards.api.util.AiProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("UserApiKeyMapper")
class UserApiKeyMapperTest {

    private UserApiKeyMapper mapper;

    private static final Long USER_ID = 1L;
    private static final Long KEY_ID = 100L;
    private static final String RAW_KEY = "sk-proj-abc12345";
    private static final String RAW_KEY_SHORT = "ab";
    private static final AiProvider PROVIDER = AiProvider.OPENAI;
    private static final Instant NOW = Instant.now();

    private User user;
    private UserApiKey entity;

    @BeforeEach
    void setUp() {
        mapper = new UserApiKeyMapper();

        user = User.builder()
            .username("testuser")
            .email("test@email.com")
            .passwordHash("hash")
            .zoneInfo("UTC")
            .addRole(User.UserRole.ROLE_USER)
            .build();
        user.setId(USER_ID);

        entity = UserApiKey.builder()
            .user(user)
            .provider(PROVIDER)
            .keyAlias("...2345")
            .encryptedKey("encrypted-value")
            .build();
        entity.setId(KEY_ID);
        entity.setCreatedAt(NOW);
    }

    @Nested
    @DisplayName("toEntity")
    class ToEntity {

        @Test
        @DisplayName("should map request to entity with all fields")
        void shouldMapToEntity() {
            CreateApiKeyRequest request = new CreateApiKeyRequest(PROVIDER, RAW_KEY);

            UserApiKey result = mapper.toEntity(user, request);

            assertThat(result.getUser()).isEqualTo(user);
            assertThat(result.getProvider()).isEqualTo(PROVIDER);
            assertThat(result.getEncryptedKey()).isEqualTo(RAW_KEY);
            assertThat(result.getKeyAlias()).isEqualTo("...2345");
        }

        @Test
        @DisplayName("should not mask short keys")
        void shouldNotMaskShortKey() {
            CreateApiKeyRequest request = new CreateApiKeyRequest(PROVIDER, RAW_KEY_SHORT);

            UserApiKey result = mapper.toEntity(user, request);

            assertThat(result.getKeyAlias()).isEqualTo(RAW_KEY_SHORT);
        }
    }

    @Nested
    @DisplayName("toResponse")
    class ToResponse {

        @Test
        @DisplayName("should map entity to response without encryptedKey")
        void shouldMapToResponse() {
            ApiKeyResponse response = mapper.toResponse(entity);

            assertThat(response.id()).isEqualTo(KEY_ID);
            assertThat(response.provider()).isEqualTo(PROVIDER);
            assertThat(response.keyAlias()).isEqualTo("...2345");
            assertThat(response.createdAt()).isEqualTo(NOW);
        }

        @Test
        @DisplayName("should not contain encryptedKey field")
        void shouldNotExposeEncryptedKey() {
            ApiKeyResponse response = mapper.toResponse(entity);

            assertThat(response)
                .hasNoNullFieldsOrPropertiesExcept();
            assertThat(response.getClass().getDeclaredFields())
                .extracting(java.lang.reflect.Field::getName)
                .doesNotContain("encryptedKey");
        }
    }

    @Test
    @DisplayName("buildKeyAlias should mask all but last 4 chars")
    void shouldBuildKeyAlias() {
        CreateApiKeyRequest request = new CreateApiKeyRequest(PROVIDER, "sk-abc12345");
        UserApiKey entity = mapper.toEntity(user, request);

        assertThat(entity.getKeyAlias()).isEqualTo("...2345");
    }

    @Test
    @DisplayName("buildKeyAlias should return original for keys shorter than 4 chars")
    void shouldReturnOriginalForShortKey() {
        CreateApiKeyRequest request = new CreateApiKeyRequest(PROVIDER, "ab");
        UserApiKey entity = mapper.toEntity(user, request);

        assertThat(entity.getKeyAlias()).isEqualTo("ab");
    }

}
