package com.cards.api.controller;

import com.cards.api.config.ApplicationConfig;
import com.cards.api.controller.user.UserApiKeyController;
import com.cards.api.dto.SecurityUser;
import com.cards.api.dto.request.CreateApiKeyRequest;
import com.cards.api.dto.response.ApiKeyResponse;
import com.cards.api.exception.ResourceNotFoundException;
import com.cards.api.exception.domain.DuplicatedApiKeyException;
import com.cards.api.infraestructure.config.Config;
import com.cards.api.repo.UserRepository;
import com.cards.api.security.SecurityConfig;
import com.cards.api.service.CustomUserDetailService;
import com.cards.api.service.JwtService;
import com.cards.api.service.UserApiKeyService;
import com.cards.api.util.AiProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@WebMvcTest(UserApiKeyController.class)
@Import({SecurityConfig.class, ApplicationConfig.class, Config.class})
@DisplayName("UserApiKeyController")
class UserApiKeyControllerTest {

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private UserApiKeyService userApiKeyService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private CustomUserDetailService customUserDetailService;

    @MockitoBean
    private UserRepository userRepository;

    private static final Long USER_ID = 1L;
    private static final Long KEY_ID = 100L;
    private static final Instant NOW = Instant.now();

    private static SecurityUser securityUser() {
        return new SecurityUser(USER_ID, "testuser", "test@email.com", "hash", "America/Buenos_Aires",
            List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    private ApiKeyResponse createResponse() {
        return new ApiKeyResponse(KEY_ID, AiProvider.OPENAI, "...2345", NOW);
    }

    // ======================== LIST ========================

    @Nested
    @DisplayName("GET /users/me/api-keys")
    class ListKeys {

        @Test
        @DisplayName("should return 200 with API keys list")
        void shouldReturnKeys() {
            when(userApiKeyService.getUserKeys(USER_ID)).thenReturn(List.of(createResponse()));

            var result = assertThat(mvc.get().uri("/users/me/api-keys")
                .with(user(securityUser())));
            result.hasStatusOk();
            result.bodyJson().extractingPath("$[0].id").asNumber().matches(n -> n.longValue() == KEY_ID);
            result.bodyJson().extractingPath("$[0].provider").asString().isEqualTo("OPENAI");
        }

        @Test
        @DisplayName("should return 403 without authentication")
        void shouldReturn403() {
            assertThat(mvc.get().uri("/users/me/api-keys"))
                .hasStatus(HttpStatus.FORBIDDEN);
        }
    }

    // ======================== CREATE ========================

    @Nested
    @DisplayName("POST /users/me/api-keys")
    class CreateKey {

        @Test
        @DisplayName("should return 201 with created API key")
        void shouldCreateKey() {
            when(userApiKeyService.createKey(anyLong(), any(CreateApiKeyRequest.class)))
                .thenReturn(createResponse());

            String body = """
                {
                    "provider": "OPENAI",
                    "apiKey": "sk-proj-abc12345"
                }
                """;

            var result = assertThat(mvc.post().uri("/users/me/api-keys")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(user(securityUser())));
            result.hasStatus(HttpStatus.CREATED);
            result.bodyJson().extractingPath("$.id").asNumber().matches(n -> n.longValue() == KEY_ID);
            result.bodyJson().extractingPath("$.provider").asString().isEqualTo("OPENAI");
            result.bodyJson().extractingPath("$.keyAlias").asString().isEqualTo("...2345");
        }

        @Test
        @DisplayName("should return 409 when provider already exists")
        void shouldReturn409WhenDuplicate() {
            when(userApiKeyService.createKey(anyLong(), any(CreateApiKeyRequest.class)))
                .thenThrow(new DuplicatedApiKeyException("OPENAI"));

            String body = """
                {
                    "provider": "OPENAI",
                    "apiKey": "sk-proj-abc12345"
                }
                """;

            assertThat(mvc.post().uri("/users/me/api-keys")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(user(securityUser())))
                .hasStatus(HttpStatus.CONFLICT);
        }

        @Test
        @DisplayName("should return 400 when apiKey is blank")
        void shouldReturn400WhenApiKeyBlank() {
            String body = """
                {
                    "provider": "OPENAI",
                    "apiKey": ""
                }
                """;

            assertThat(mvc.post().uri("/users/me/api-keys")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(user(securityUser())))
                .hasStatus(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("should return 400 when provider is null")
        void shouldReturn400WhenProviderNull() {
            String body = """
                {
                    "apiKey": "sk-proj-abc12345"
                }
                """;

            assertThat(mvc.post().uri("/users/me/api-keys")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(user(securityUser())))
                .hasStatus(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("should return 403 without authentication")
        void shouldReturn403() {
            String body = """
                {
                    "provider": "OPENAI",
                    "apiKey": "sk-proj-abc12345"
                }
                """;

            assertThat(mvc.post().uri("/users/me/api-keys")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .hasStatus(HttpStatus.FORBIDDEN);
        }
    }

    // ======================== DELETE ========================

    @Nested
    @DisplayName("DELETE /users/me/api-keys/{keyId}")
    class DeleteKey {

        @Test
        @DisplayName("should return 204 on successful delete")
        void shouldDeleteKey() {
            assertThat(mvc.delete().uri("/users/me/api-keys/{keyId}", KEY_ID)
                .with(user(securityUser())))
                .hasStatus(HttpStatus.NO_CONTENT);
        }

        @Test
        @DisplayName("should return 404 when key not found")
        void shouldReturn404() {
            doThrow(new ResourceNotFoundException("API key not found"))
                .when(userApiKeyService).deleteKey(anyLong(), anyLong());

            assertThat(mvc.delete().uri("/users/me/api-keys/{keyId}", KEY_ID)
                .with(user(securityUser())))
                .hasStatus(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("should return 403 without authentication")
        void shouldReturn403() {
            assertThat(mvc.delete().uri("/users/me/api-keys/{keyId}", KEY_ID))
                .hasStatus(HttpStatus.FORBIDDEN);
        }
    }
}
