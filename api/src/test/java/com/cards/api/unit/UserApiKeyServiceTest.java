package com.cards.api.unit;

import com.cards.api.dto.request.CreateApiKeyRequest;
import com.cards.api.dto.response.ApiKeyResponse;
import com.cards.api.entity.User;
import com.cards.api.entity.UserApiKey;
import com.cards.api.exception.ResourceNotFoundException;
import com.cards.api.exception.domain.DuplicatedApiKeyException;
import com.cards.api.mapper.UserApiKeyMapper;
import com.cards.api.repo.UserApiKeyRepository;
import com.cards.api.repo.UserRepository;
import com.cards.api.service.UserApiKeyService;
import com.cards.api.util.AiProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserApiKeyService")
class UserApiKeyServiceTest {

    @Mock
    private UserApiKeyRepository repository;

    @Mock
    private UserRepository userRepo;

    @Mock
    private UserApiKeyMapper mapper;

    private UserApiKeyService service;

    private static final Long USER_ID = 1L;
    private static final Long KEY_ID = 100L;
    private static final Long ADMIN_TARGET_USER_ID = 2L;
    private static final AiProvider PROVIDER = AiProvider.OPENAI;
    private static final String RAW_KEY = "sk-proj-abc12345";
    private static final Instant NOW = Instant.now();

    private User owner;
    private UserApiKey entity;
    private CreateApiKeyRequest createRequest;
    private ApiKeyResponse response;

    @BeforeEach
    void setUp() {
        service = new UserApiKeyService(repository, userRepo, mapper);

        owner = User.builder()
            .username("owner")
            .email("owner@email.com")
            .passwordHash("hash")
            .zoneInfo("UTC")
            .addRole(User.UserRole.ROLE_USER)
            .build();
        owner.setId(USER_ID);

        entity = UserApiKey.builder()
            .user(owner)
            .provider(PROVIDER)
            .keyAlias("...2345")
            .encryptedKey("encrypted-value")
            .build();
        entity.setId(KEY_ID);
        entity.setCreatedAt(NOW);

        createRequest = new CreateApiKeyRequest(PROVIDER, RAW_KEY);

        response = new ApiKeyResponse(KEY_ID, PROVIDER, "...2345", NOW);
    }

    // ======================== GET USER KEYS ========================

    @Nested
    @DisplayName("getUserKeys")
    class GetUserKeys {

        @Test
        @DisplayName("should return list of API keys for user")
        void shouldReturnUserKeys() {
            when(repository.findByUserId(USER_ID)).thenReturn(List.of(entity));
            when(mapper.toResponse(entity)).thenReturn(response);

            List<ApiKeyResponse> result = service.getUserKeys(USER_ID);

            assertThat(result).hasSize(1);
            assertThat(result.getFirst().id()).isEqualTo(KEY_ID);
        }

        @Test
        @DisplayName("should return empty list when user has no keys")
        void shouldReturnEmptyListWhenNoKeys() {
            when(repository.findByUserId(USER_ID)).thenReturn(List.of());

            List<ApiKeyResponse> result = service.getUserKeys(USER_ID);

            assertThat(result).isEmpty();
        }
    }

    // ======================== CREATE KEY ========================

    @Nested
    @DisplayName("createKey")
    class CreateKey {

        @Test
        @DisplayName("should create API key successfully")
        void shouldCreateKey() {
            when(repository.existsByUserIdAndProvider(USER_ID, PROVIDER)).thenReturn(false);
            when(userRepo.getReferenceById(USER_ID)).thenReturn(owner);
            when(mapper.toEntity(owner, createRequest)).thenReturn(entity);
            when(repository.save(entity)).thenReturn(entity);
            when(mapper.toResponse(entity)).thenReturn(response);

            ApiKeyResponse result = service.createKey(USER_ID, createRequest);

            assertThat(result.id()).isEqualTo(KEY_ID);
            assertThat(result.provider()).isEqualTo(PROVIDER);
        }

        @Test
        @DisplayName("should throw DuplicatedApiKeyException when key exists for provider")
        void shouldThrowWhenKeyExists() {
            when(repository.existsByUserIdAndProvider(USER_ID, PROVIDER)).thenReturn(true);

            assertThatThrownBy(() -> service.createKey(USER_ID, createRequest))
                .isInstanceOf(DuplicatedApiKeyException.class)
                .hasMessageContaining(PROVIDER.name());

            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("should catch DataIntegrityViolationException and throw DuplicatedApiKeyException")
        void shouldCatchDataIntegrityViolation() {
            when(repository.existsByUserIdAndProvider(USER_ID, PROVIDER)).thenReturn(false);
            when(userRepo.getReferenceById(USER_ID)).thenReturn(owner);
            when(mapper.toEntity(owner, createRequest)).thenReturn(entity);
            when(repository.save(entity)).thenThrow(new DataIntegrityViolationException("unique constraint"));

            assertThatThrownBy(() -> service.createKey(USER_ID, createRequest))
                .isInstanceOf(DuplicatedApiKeyException.class)
                .hasMessageContaining(PROVIDER.name());
        }
    }

    // ======================== DELETE KEY ========================

    @Nested
    @DisplayName("deleteKey")
    class DeleteKey {

        @Test
        @DisplayName("should delete key when it belongs to user")
        void shouldDeleteKey() {
            when(repository.findByIdAndUserId(KEY_ID, USER_ID)).thenReturn(Optional.of(entity));

            service.deleteKey(USER_ID, KEY_ID);

            verify(repository).<UserApiKey>delete(entity);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when key not found")
        void shouldThrowWhenKeyNotFound() {
            when(repository.findByIdAndUserId(KEY_ID, USER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.deleteKey(USER_ID, KEY_ID))
                .isInstanceOf(ResourceNotFoundException.class);

            verify(repository, never()).delete(any(UserApiKey.class));
        }
    }

    // ======================== GET DECRYPTED KEY ========================

    @Nested
    @DisplayName("getDecryptedKey")
    class GetDecryptedKey {

        @Test
        @DisplayName("should return decrypted key value")
        void shouldReturnDecryptedKey() {
            when(repository.findByUserIdAndProvider(USER_ID, PROVIDER)).thenReturn(Optional.of(entity));

            String result = service.getDecryptedKey(USER_ID, PROVIDER);

            assertThat(result).isEqualTo("encrypted-value");
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when no key for provider")
        void shouldThrowWhenNotFound() {
            when(repository.findByUserIdAndProvider(USER_ID, PROVIDER)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getDecryptedKey(USER_ID, PROVIDER))
                .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ======================== ADMIN ========================

    @Nested
    @DisplayName("admin operations")
    class AdminOperations {

        @Test
        @DisplayName("getAdminUserKeys should return keys for any user")
        void shouldGetAdminUserKeys() {
            when(repository.findByUserId(ADMIN_TARGET_USER_ID)).thenReturn(List.of(entity));
            when(mapper.toResponse(entity)).thenReturn(response);

            List<ApiKeyResponse> result = service.getAdminUserKeys(ADMIN_TARGET_USER_ID);

            assertThat(result).hasSize(1);
            assertThat(result.getFirst().id()).isEqualTo(KEY_ID);
        }

        @Test
        @DisplayName("adminDeleteKey should delete any key by id")
        void shouldAdminDeleteKey() {
            when(repository.findById(KEY_ID)).thenReturn(Optional.of(entity));

            service.adminDeleteKey(KEY_ID);

            verify(repository).<UserApiKey>delete(entity);
        }

        @Test
        @DisplayName("adminDeleteKey should throw ResourceNotFoundException")
        void shouldThrowWhenKeyNotFound() {
            when(repository.findById(KEY_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.adminDeleteKey(KEY_ID))
                .isInstanceOf(ResourceNotFoundException.class);

            verify(repository, never()).delete(any(UserApiKey.class));
        }
    }
}
