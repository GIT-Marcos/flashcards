package com.cards.api.controller;

import com.cards.api.config.ApplicationConfig;
import com.cards.api.controller.user.UserController;
import com.cards.api.dto.SecurityUser;
import com.cards.api.dto.request.PatchUserRequest;
import com.cards.api.dto.response.UserResponse;
import com.cards.api.exception.ResourceNotFoundException;
import com.cards.api.exception.domain.DuplicatedUsernameException;
import com.cards.api.infraestructure.config.Config;
import com.cards.api.repo.UserRepository;
import com.cards.api.security.SecurityConfig;
import com.cards.api.service.CustomUserDetailService;
import com.cards.api.service.JwtService;
import com.cards.api.service.UserService;
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

import java.util.List;
import java.util.Set;

import static java.time.Instant.now;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@WebMvcTest(UserController.class)
@Import({SecurityConfig.class, ApplicationConfig.class, Config.class})
@DisplayName("UserController")
class UserControllerTest {

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private UserService userService;
    @MockitoBean
    private JwtService jwtService;
    @MockitoBean
    private CustomUserDetailService customUserDetailService;
    @MockitoBean
    private UserRepository userRepository;

    private static final Long USER_ID = 1L;

    private static SecurityUser securityUser() {
        return new SecurityUser(USER_ID, "testuser", "test@email.com", "hash", "America/Buenos_Aires",
            List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    private UserResponse createUserResponse() {
        return new UserResponse(USER_ID, "testuser", "test@email.com", "America/Buenos_Aires",
            now(), now(), now(), 30, 6, true, Set.of("ROLE_USER"));
    }

    private String validPatchBody() {
        return """
            {
                "username": "testuser",
                "email": "test@email.com",
                "sessionThreshold": 30,
                "startOfDay": 6,
                "notificationsEnabled": true,
                "currentPassword": "CurrentP@ss1",
                "password": "Str0ngP@ss!"
            }
            """;
    }

    // ======================== GET CURRENT USER ========================

    @Nested
    @DisplayName("GET /users/me")
    class GetCurrentUser {

        @Test
        @DisplayName("should return 200 with user data")
        void shouldReturnCurrentUser() {
            when(userService.getCurrent(anyLong())).thenReturn(createUserResponse());

            var result = assertThat(mvc.get().uri("/users/me")
                .with(user(securityUser())));
            result.hasStatusOk();
            result.bodyJson().extractingPath("$.id").asNumber().matches(n -> n.longValue() == USER_ID);
            result.bodyJson().extractingPath("$.username").asString().isEqualTo("testuser");
            result.bodyJson().extractingPath("$.email").asString().isEqualTo("test@email.com");
            result.bodyJson().extractingPath("$.zone").asString().isEqualTo("America/Buenos_Aires");
            result.bodyJson().extractingPath("$.sessionThreshold").asNumber().isEqualTo(30);
            result.bodyJson().extractingPath("$.startOfDay").asNumber().isEqualTo(6);
            result.bodyJson().extractingPath("$.notificationsEnabled").asBoolean().isTrue();
            result.bodyJson().extractingPath("$.roles").asArray();
        }

        @Test
        @DisplayName("should return 404 when user not found")
        void shouldReturn404() {
            when(userService.getCurrent(anyLong()))
                .thenThrow(new ResourceNotFoundException("User not found"));

            assertThat(mvc.get().uri("/users/me")
                .with(user(securityUser())))
                .hasStatus(HttpStatus.NOT_FOUND);
        }

    }

    // ======================== PATCH ========================

    @Nested
    @DisplayName("PATCH /users/me")
    class Patch {

        @Test
        @DisplayName("should return 200 with updated user data")
        void shouldPatchUser() {
            when(userService.patch(anyLong(), any(PatchUserRequest.class)))
                .thenReturn(createUserResponse());

            String body = """
                {
                    "username": "newname",
                    "email": "test@email.com",
                    "sessionThreshold": 30,
                    "startOfDay": 6,
                    "currentPassword": "CurrentP@ss1",
                    "password": "Str0ngP@ss!"
                }
                """;

            var result = assertThat(mvc.patch().uri("/users/me")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(user(securityUser())));
            result.hasStatusOk();
            result.bodyJson().extractingPath("$.username").asString().isEqualTo("testuser");
        }

        @Test
        @DisplayName("should return 409 when username is duplicated")
        void shouldReturn409ForDuplicatedUsername() {
            when(userService.patch(anyLong(), any(PatchUserRequest.class)))
                .thenThrow(new DuplicatedUsernameException("taken"));

            String body = """
                {
                    "username": "taken",
                    "email": "test@email.com",
                    "sessionThreshold": 30,
                    "startOfDay": 6,
                    "currentPassword": "CurrentP@ss1",
                    "password": "Str0ngP@ss!"
                }
                """;

            assertThat(mvc.patch().uri("/users/me")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(user(securityUser())))
                .hasStatus(HttpStatus.CONFLICT);
        }

        @Test
        @DisplayName("should return 200 updating only email")
        void shouldPatchEmailOnly() {
            when(userService.patch(anyLong(), any(PatchUserRequest.class)))
                .thenReturn(createUserResponse());

            String body = """
                {
                    "username": "testuser",
                    "email": "new@email.com",
                    "sessionThreshold": 30,
                    "startOfDay": 6,
                    "currentPassword": "CurrentP@ss1",
                    "password": "Str0ngP@ss!"
                }
                """;

            var result = assertThat(mvc.patch().uri("/users/me")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(user(securityUser())));
            result.hasStatusOk();
            result.bodyJson().extractingPath("$.email").asString().isEqualTo("test@email.com");
        }

        @Test
        @DisplayName("should return 403 without authentication")
        void shouldReturn403() {
            String body = """
                {
                    "username": "newname",
                    "email": "test@email.com",
                    "sessionThreshold": 30,
                    "startOfDay": 6,
                    "currentPassword": "CurrentP@ss1",
                    "password": "Str0ngP@ss!"
                }
                """;

            assertThat(mvc.patch().uri("/users/me")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .hasStatus(HttpStatus.FORBIDDEN);
        }
    }

    // ======================== DELETE ========================

    @Nested
    @DisplayName("DELETE /users/me")
    class DeleteAccount {

        @Test
        @DisplayName("should return 204 on successful deletion")
        void shouldDeleteAccount() {
            assertThat(mvc.delete().uri("/users/me")
                .with(user(securityUser())))
                .hasStatus(HttpStatus.NO_CONTENT);
        }

        @Test
        @DisplayName("should return 404 when user not found")
        void shouldReturn404() {
            org.mockito.Mockito.doThrow(new ResourceNotFoundException("User not found"))
                .when(userService).deleteUser(anyLong());

            assertThat(mvc.delete().uri("/users/me")
                .with(user(securityUser())))
                .hasStatus(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("should return 403 without authentication")
        void shouldReturn403() {
            assertThat(mvc.delete().uri("/users/me"))
                .hasStatus(HttpStatus.FORBIDDEN);
        }
    }
}
