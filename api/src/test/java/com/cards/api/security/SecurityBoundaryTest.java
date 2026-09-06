package com.cards.api.security;

import com.cards.api.config.ApplicationConfig;
import com.cards.api.controller.admin.AdminController;
import com.cards.api.controller.auth.AuthController;
import com.cards.api.controller.flashcard.CardController;
import com.cards.api.controller.flashcard.DeckController;
import com.cards.api.controller.flashcard.ReviewController;
import com.cards.api.controller.flashcard.SessionController;
import com.cards.api.controller.user.UserController;
import com.cards.api.controller.verification.EmailVerificationController;
import com.cards.api.dto.SecurityUser;
import com.cards.api.dto.response.AuthResponse;
import com.cards.api.dto.response.SignupResponse;
import com.cards.api.dto.response.UserResponse;
import com.cards.api.infraestructure.config.Config;
import com.cards.api.repo.UserRepository;
import com.cards.api.service.*;
import com.cards.api.service.ai.AiCardGeneratorService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.ScrollPosition;
import org.springframework.data.domain.Window;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

@WebMvcTest({
    AuthController.class,
    AdminController.class,
    UserController.class,
    DeckController.class,
    CardController.class,
    ReviewController.class,
    SessionController.class,
    EmailVerificationController.class,
})
@Import({SecurityConfig.class, ApplicationConfig.class, Config.class})
@DisplayName("Security boundaries")
class SecurityBoundaryTest {

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private AuthService authService;
    @MockitoBean
    private AdminService adminService;
    @MockitoBean
    private UserService userService;
    @MockitoBean
    private CardService cardService;
    @MockitoBean
    private DeckService deckService;
    @MockitoBean
    private ReviewService reviewService;
    @MockitoBean
    private SessionService sessionService;
    @MockitoBean
    private StatsService statsService;
    @MockitoBean
    private JwtService jwtService;
    @MockitoBean
    private CustomUserDetailService customUserDetailService;
    @MockitoBean
    private UserRepository userRepository;
    @MockitoBean
    private VerificationService verificationService;
    @MockitoBean
    private AiCardGeneratorService aiCardGeneratorService;

    private static final AuthResponse MOCK_AUTH_RESPONSE =
        new AuthResponse("access-token", "refresh-token", "testuser");

    private static SecurityUser securityUser(String username, String role) {
        return new SecurityUser(1L, username, username + "@test.com", "hash", "UTC",
            List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }

    // ======================== PUBLIC ENDPOINTS (/auth/**) ========================

    @Nested
    @DisplayName("Public endpoints (/auth/**)")
    class PublicEndpoints {

        @Test
        @DisplayName("POST /auth/signup should be accessible without authentication")
        void signupShouldBePublic() {
            when(authService.signup(any())).thenReturn(new SignupResponse("ok"));

            assertThat(mvc.post().uri("/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "username": "testuser",
                        "email": "test@email.com",
                        "password": "Str0ngP@ss!",
                        "zoneInfo": "America/Buenos_Aires"
                    }
                    """))
                .hasStatus(HttpStatus.ACCEPTED);
        }

        @Test
        @DisplayName("POST /auth/login should be accessible without authentication")
        void loginShouldBePublic() {
            when(authService.login(any(), any())).thenReturn(MOCK_AUTH_RESPONSE);

            assertThat(mvc.post().uri("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "username": "testuser",
                        "password": "Str0ngP@ss!"
                    }
                    """))
                .hasStatus(HttpStatus.OK);
        }

        @Test
        @DisplayName("POST /auth/logout should be accessible without authentication")
        void logoutShouldBePublic() {
            assertThat(mvc.post().uri("/auth/logout"))
                .hasStatus(HttpStatus.NO_CONTENT);
        }

        @Test
        @DisplayName("GET /auth/confirm should be accessible without authentication")
        void getConfirmShouldBePublic() {
            assertThat(mvc.get().uri("/auth/confirm")
                .param("token", "some-token"))
                .hasStatus(HttpStatus.OK);
        }

        @Test
        @DisplayName("POST /auth/confirm should be accessible without authentication")
        void postConfirmShouldBePublic() {
            assertThat(mvc.post().uri("/auth/confirm")
                .param("token", "some-token"))
                .hasStatus(HttpStatus.OK);
        }

        @Test
        @DisplayName("POST /auth/forgot-password should be accessible without authentication")
        void forgotPasswordShouldBePublic() {
            when(authService.forgotPassword(any())).thenReturn(
                new com.cards.api.dto.response.ForgotPasswordResponse("ok"));

            assertThat(mvc.post().uri("/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "email": "user@example.com"
                    }
                    """))
                .hasStatus(HttpStatus.ACCEPTED);
        }

        @Test
        @DisplayName("POST /auth/reset-password should be accessible without authentication")
        void resetPasswordShouldBePublic() {
            when(authService.resetPassword(any())).thenReturn(
                new com.cards.api.dto.response.ResetPasswordResponse("ok"));

            assertThat(mvc.post().uri("/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "token": "valid-jwt",
                        "newPassword": "NewStr0ngP@ss!"
                    }
                    """))
                .hasStatus(HttpStatus.OK);
        }
    }

    // ======================== PROTECTED ENDPOINTS ========================

    @Nested
    @DisplayName("Protected endpoints require authentication")
    class ProtectedEndpoints {

        @Test
        @DisplayName("GET /users/me should return 403 without authentication")
        void usersMeRequiresAuth() {
            assertThat(mvc.get().uri("/users/me"))
                .hasStatus(HttpStatus.FORBIDDEN);
        }

        @Test
        @DisplayName("GET /decks should return 403 without authentication")
        void decksRequiresAuth() {
            assertThat(mvc.get().uri("/decks"))
                .hasStatus(HttpStatus.FORBIDDEN);
        }

        @Test
        @DisplayName("GET /sessions should return 403 without authentication")
        void sessionsRequiresAuth() {
            assertThat(mvc.get().uri("/sessions"))
                .hasStatus(HttpStatus.FORBIDDEN);
        }

        @Test
        @DisplayName("POST /reviews/card/1 should return 403 without authentication")
        void reviewsRequiresAuth() {
            assertThat(mvc.post().uri("/reviews/card/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "quality": 4
                    }
                    """))
                .hasStatus(HttpStatus.FORBIDDEN);
        }

        @Test
        @DisplayName("GET /users/me should return 200 with authenticated SecurityUser")
        void usersMeWithAuth() {
            when(userService.getCurrent(1L)).thenReturn(null);

            assertThat(mvc.get().uri("/users/me")
                .with(user(securityUser("testuser", "USER"))))
                .hasStatus(HttpStatus.OK);
        }
    }

    // ======================== ADMIN ENDPOINTS ========================

    @Nested
    @DisplayName("Admin endpoint authorization")
    class AdminEndpoints {

        @Test
        @DisplayName("GET /admin/users should return 403 without authentication")
        void adminUsersRequiresAuth() {
            assertThat(mvc.get().uri("/admin/users"))
                .hasStatus(HttpStatus.FORBIDDEN);
        }

        @Test
        @DisplayName("GET /admin/users should return 403 for USER role")
        void adminUsersForbiddenForUser() {
            assertThat(mvc.get().uri("/admin/users")
                .with(user(securityUser("regularuser", "USER"))))
                .hasStatus(HttpStatus.FORBIDDEN);
        }

        @Test
        @DisplayName("DELETE /admin/users/1 should return 403 for USER role")
        void adminDeleteForbiddenForUser() {
            assertThat(mvc.delete().uri("/admin/users/1")
                .with(user(securityUser("regularuser", "USER"))))
                .hasStatus(HttpStatus.FORBIDDEN);
        }

        @Test
        @DisplayName("GET /admin/users should return 200 for ADMIN role")
        void adminUsersAllowedForAdmin() {
            Window<UserResponse> empty = Window.from(
                List.of(),
                i -> ScrollPosition.keyset(),
                false
            );
            when(adminService.getAllUsers(any())).thenReturn(empty);

            assertThat(mvc.get().uri("/admin/users")
                .with(user(securityUser("admin", "ADMIN"))))
                .hasStatus(HttpStatus.OK);
        }
    }

    // ======================== ROLE ESCALATION ========================

    @Nested
    @DisplayName("Role escalation prevention")
    class RoleEscalation {

        @Test
        @DisplayName("USER with valid session cannot access admin endpoints")
        void userRoleCannotEscalateToAdmin() {
            var userAuth = user(securityUser("attacker", "USER"));

            assertThat(mvc.get().uri("/admin/users").with(userAuth))
                .hasStatus(HttpStatus.FORBIDDEN);

            assertThat(mvc.delete().uri("/admin/users/1").with(userAuth))
                .hasStatus(HttpStatus.FORBIDDEN);

            assertThat(mvc.delete().uri("/admin/decks/1").with(userAuth))
                .hasStatus(HttpStatus.FORBIDDEN);
        }

        @Test
        @DisplayName("Non-existent role can access authenticated user endpoints but not admin")
        void nonExistentRoleAccess() {
            var unknownAuth = user(securityUser("hacker", "NONEXISTENT"));

            assertThat(mvc.get().uri("/users/me").with(unknownAuth))
                .hasStatus(HttpStatus.OK);

            assertThat(mvc.get().uri("/admin/users").with(unknownAuth))
                .hasStatus(HttpStatus.FORBIDDEN);
        }
    }

    // ======================== CSRF ========================

    @Nested
    @DisplayName("CSRF disabled (stateless API — SameSite cookie mitigation)")
    class CsrfProtection {

        @Test
        @DisplayName("DELETE /decks/1 should work without CSRF token (stateless API)")
        void deleteDeckNoCsrfRequired() {
            assertThat(mvc.delete().uri("/decks/1")
                .with(user(securityUser("testuser", "USER"))))
                .hasStatus(HttpStatus.NO_CONTENT);
        }
    }
}
