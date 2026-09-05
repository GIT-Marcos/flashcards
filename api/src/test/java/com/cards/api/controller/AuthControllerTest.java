package com.cards.api.controller;

import com.cards.api.config.ApplicationConfig;
import com.cards.api.controller.auth.AuthController;
import com.cards.api.dto.request.ForgotPasswordRequest;
import com.cards.api.dto.request.LoginRequest;
import com.cards.api.dto.request.RegisterRequest;
import com.cards.api.dto.request.ResetPasswordRequest;
import com.cards.api.dto.response.AuthResponse;
import com.cards.api.dto.response.ForgotPasswordResponse;
import com.cards.api.dto.response.ResetPasswordResponse;
import com.cards.api.dto.response.SignupResponse;
import com.cards.api.exception.domain.DuplicatedUserEmailException;
import com.cards.api.exception.domain.DuplicatedUsernameException;
import com.cards.api.exception.domain.InvalidResetPasswordTokenException;
import com.cards.api.exception.domain.InvalidTimeZoneException;
import com.cards.api.infraestructure.config.Config;
import com.cards.api.security.SecurityConfig;
import com.cards.api.service.AuthService;
import com.cards.api.service.CustomUserDetailService;
import com.cards.api.service.JwtService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, ApplicationConfig.class, Config.class})
@DisplayName("AuthController")
class AuthControllerTest {

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private AuthService authService;
    @MockitoBean
    private JwtService jwtService;
    @MockitoBean
    private CustomUserDetailService customUserDetailService;
    @MockitoBean
    private com.cards.api.repo.UserRepository userRepository;

    private static final String VALID_ACCESS_TOKEN = "access-token-xyz";
    private static final String VALID_REFRESH_TOKEN = "refresh-token-xyz";

    private String validRegisterJson() {
        return """
            {
                "username": "testuser",
                "email": "test@email.com",
                "password": "Str0ngP@ss!",
                "zoneInfo": "America/Buenos_Aires"
            }
            """;
    }

    private String validLoginJson() {
        return """
            {
                "username": "testuser",
                "password": "Str0ngP@ss!"
            }
            """;
    }

    private AuthResponse successResponse() {
        return new AuthResponse(VALID_ACCESS_TOKEN, VALID_REFRESH_TOKEN, "testuser");
    }

    private SignupResponse signupResponse() {
        return new SignupResponse("Se ha enviado un email de verificación a test@email.com");
    }

    private ForgotPasswordResponse forgotPasswordResponse() {
        return new ForgotPasswordResponse("If an account with that email exists, a password reset link has been sent.");
    }

    private ResetPasswordResponse resetPasswordResponse() {
        return new ResetPasswordResponse("Your password has been successfully reset.");
    }

    // ======================== SIGNUP ========================

    @Nested
    @DisplayName("POST /auth/signup")
    class Signup {

        @Test
        @DisplayName("should return 202 with message")
        void shouldSignupSuccessfully() {
            when(authService.signup(any(RegisterRequest.class))).thenReturn(signupResponse());

            var result = assertThat(mvc.post().uri("/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRegisterJson()));
            result.hasStatus(HttpStatus.ACCEPTED);
            result.bodyJson().extractingPath("$.message").asString().contains("test@email.com");
        }

        @Test
        @DisplayName("should return 400 when username is blank")
        void shouldReturn400WhenUsernameBlank() {
            String body = """
                {
                    "username": "",
                    "email": "test@email.com",
                    "password": "Str0ngP@ss!",
                    "zoneInfo": "America/Buenos_Aires"
                }
                """;

            assertThat(mvc.post().uri("/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .hasStatus(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("should return 400 when username is too short")
        void shouldReturn400WhenUsernameTooShort() {
            String body = """
                {
                    "username": "ab",
                    "email": "test@email.com",
                    "password": "Str0ngP@ss!",
                    "zoneInfo": "America/Buenos_Aires"
                }
                """;

            assertThat(mvc.post().uri("/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .hasStatus(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("should return 400 when email is invalid")
        void shouldReturn400WhenEmailInvalid() {
            String body = """
                {
                    "username": "testuser",
                    "email": "not-an-email",
                    "password": "Str0ngP@ss!",
                    "zoneInfo": "America/Buenos_Aires"
                }
                """;

            assertThat(mvc.post().uri("/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .hasStatus(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("should return 400 when password is weak")
        void shouldReturn400WhenPasswordWeak() {
            String body = """
                {
                    "username": "testuser",
                    "email": "test@email.com",
                    "password": "weak",
                    "zoneInfo": "America/Buenos_Aires"
                }
                """;

            assertThat(mvc.post().uri("/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .hasStatus(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("should return 400 when password lacks uppercase")
        void shouldReturn400WhenPasswordNoUppercase() {
            String body = """
                {
                    "username": "testuser",
                    "email": "test@email.com",
                    "password": "str0ngp@ss!",
                    "zoneInfo": "America/Buenos_Aires"
                }
                """;

            assertThat(mvc.post().uri("/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .hasStatus(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("should return 400 when password lacks special character")
        void shouldReturn400WhenPasswordNoSpecial() {
            String body = """
                {
                    "username": "testuser",
                    "email": "test@email.com",
                    "password": "Str0ngPass",
                    "zoneInfo": "America/Buenos_Aires"
                }
                """;

            assertThat(mvc.post().uri("/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .hasStatus(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("should return 400 when timezone is blank")
        void shouldReturn400WhenTimezoneBlank() {
            String body = """
                {
                    "username": "testuser",
                    "email": "test@email.com",
                    "password": "Str0ngP@ss!",
                    "zoneInfo": ""
                }
                """;

            assertThat(mvc.post().uri("/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .hasStatus(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("should return 400 when service throws InvalidTimeZoneException")
        void shouldReturn400ForInvalidTimeZone() {
            when(authService.signup(any(RegisterRequest.class)))
                .thenThrow(new InvalidTimeZoneException("Invalid/Zone"));

            assertThat(mvc.post().uri("/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRegisterJson()))
                .hasStatus(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("should return 409 when username is duplicated")
        void shouldReturn409ForDuplicatedUsername() {
            when(authService.signup(any(RegisterRequest.class)))
                .thenThrow(new DuplicatedUsernameException("testuser"));

            assertThat(mvc.post().uri("/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRegisterJson()))
                .hasStatus(HttpStatus.CONFLICT);
        }

        @Test
        @DisplayName("should return 409 when email is duplicated")
        void shouldReturn409ForDuplicatedEmail() {
            when(authService.signup(any(RegisterRequest.class)))
                .thenThrow(new DuplicatedUserEmailException("test@email.com"));

            assertThat(mvc.post().uri("/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRegisterJson()))
                .hasStatus(HttpStatus.CONFLICT);
        }
    }

    // ======================== FORGOT PASSWORD ========================

    @Nested
    @DisplayName("POST /auth/forgot-password")
    class ForgotPassword {

        @Test
        @DisplayName("should return 202 with generic message")
        void shouldReturn202() {
            when(authService.forgotPassword(any(ForgotPasswordRequest.class)))
                .thenReturn(forgotPasswordResponse());

            var result = assertThat(mvc.post().uri("/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "email": "user@example.com"
                    }
                    """));
            result.hasStatus(HttpStatus.ACCEPTED);
            result.bodyJson().extractingPath("$.message").asString()
                .contains("password reset link has been sent");
        }

        @Test
        @DisplayName("should return 400 when email is blank")
        void shouldReturn400WhenEmailBlank() {
            assertThat(mvc.post().uri("/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "email": ""
                    }
                    """))
                .hasStatus(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("should return 400 when email is invalid")
        void shouldReturn400WhenEmailInvalid() {
            assertThat(mvc.post().uri("/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "email": "not-an-email"
                    }
                    """))
                .hasStatus(HttpStatus.BAD_REQUEST);
        }
    }

    // ======================== RESET PASSWORD ========================

    @Nested
    @DisplayName("POST /auth/reset-password")
    class ResetPassword {

        @Test
        @DisplayName("should return 200 with success message")
        void shouldReturn200() {
            when(authService.resetPassword(any(ResetPasswordRequest.class)))
                .thenReturn(resetPasswordResponse());

            var result = assertThat(mvc.post().uri("/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "token": "valid-jwt-token",
                        "newPassword": "NewStr0ngP@ss!"
                    }
                    """));
            result.hasStatusOk();
            result.bodyJson().extractingPath("$.message").asString().contains("successfully");
        }

        @Test
        @DisplayName("should return 400 when token is blank")
        void shouldReturn400WhenTokenBlank() {
            assertThat(mvc.post().uri("/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "token": "",
                        "newPassword": "NewStr0ngP@ss!"
                    }
                    """))
                .hasStatus(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("should return 400 when newPassword fails validation")
        void shouldReturn400WhenPasswordInvalid() {
            assertThat(mvc.post().uri("/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "token": "valid-jwt-token",
                        "newPassword": "weak"
                    }
                    """))
                .hasStatus(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("should return 400 when service throws InvalidResetPasswordTokenException")
        void shouldReturn400WhenTokenInvalid() {
            when(authService.resetPassword(any(ResetPasswordRequest.class)))
                .thenThrow(new InvalidResetPasswordTokenException("The reset link is invalid or has expired."));

            assertThat(mvc.post().uri("/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "token": "invalid-token",
                        "newPassword": "NewStr0ngP@ss!"
                    }
                    """))
                .hasStatus(HttpStatus.BAD_REQUEST);
        }
    }

    // ======================== LOGIN ========================

    @Nested
    @DisplayName("POST /auth/login")
    class Login {

        @Test
        @DisplayName("should return 200 with access token and Set-Cookie header")
        void shouldLoginSuccessfully() {
            when(authService.login(any(LoginRequest.class), any())).thenReturn(successResponse());

            var result = assertThat(mvc.post().uri("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validLoginJson()));
            result.hasStatusOk();
            result.bodyJson().extractingPath("$.accessToken").asString().isEqualTo(VALID_ACCESS_TOKEN);
            result.bodyJson().extractingPath("$.username").asString().isEqualTo("testuser");
            result.containsHeader("Set-Cookie");
        }

        @Test
        @DisplayName("should return 401 when credentials are invalid")
        void shouldReturn401ForBadCredentials() {
            when(authService.login(any(LoginRequest.class), any()))
                .thenThrow(new BadCredentialsException("Bad credentials"));

            var result = assertThat(mvc.post().uri("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validLoginJson()));
            result.hasStatus(HttpStatus.UNAUTHORIZED);
            result.bodyJson().extractingPath("$.title").asString().isEqualTo("Invalid Credentials");
        }

        @Test
        @DisplayName("should return 400 when username is blank")
        void shouldReturn400WhenUsernameBlank() {
            String body = """
                {
                    "username": "",
                    "password": "Str0ngP@ss!"
                }
                """;

            assertThat(mvc.post().uri("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .hasStatus(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("should return 400 when password is blank")
        void shouldReturn400WhenPasswordBlank() {
            String body = """
                {
                    "username": "testuser",
                    "password": ""
                }
                """;

            assertThat(mvc.post().uri("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .hasStatus(HttpStatus.BAD_REQUEST);
        }
    }

    // ======================== REFRESH TOKEN ========================

    @Nested
    @DisplayName("POST /auth/refresh-token")
    class RefreshToken {

        @Test
        @DisplayName("should return 200 with new tokens and Set-Cookie")
        void shouldRefreshSuccessfully() {
            when(authService.refreshToken(anyString())).thenReturn(successResponse());

            var result = assertThat(mvc.post().uri("/auth/refresh-token")
                .cookie(new jakarta.servlet.http.Cookie("refresh_token", VALID_REFRESH_TOKEN)));
            result.hasStatusOk();
            result.bodyJson().extractingPath("$.accessToken").asString().isEqualTo(VALID_ACCESS_TOKEN);
            result.containsHeader("Set-Cookie");
        }

        @Test
        @DisplayName("should return 400 when refresh_token cookie is missing")
        void shouldReturn400WhenCookieMissing() {
            assertThat(mvc.post().uri("/auth/refresh-token"))
                .hasStatus(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("should delegate exception when refresh token is invalid")
        void shouldReturn401WhenTokenInvalid() {
            when(authService.refreshToken(anyString()))
                .thenThrow(new BadCredentialsException("Invalid token"));

            assertThat(mvc.post().uri("/auth/refresh-token")
                .cookie(new jakarta.servlet.http.Cookie("refresh_token", "invalid-token")))
                .hasStatus(HttpStatus.UNAUTHORIZED);
        }
    }

    // ======================== LOGOUT ========================

    @Nested
    @DisplayName("POST /auth/logout")
    class Logout {

        @Test
        @DisplayName("should return 204 with cookie cleared (maxAge=0)")
        void shouldLogoutSuccessfully() {
            var result = assertThat(mvc.post().uri("/auth/logout"));
            result.hasStatus(HttpStatus.NO_CONTENT);
            result.containsHeader("Set-Cookie");
        }
    }

    // ======================== COOKIE SECURITY ========================

    @Nested
    @DisplayName("Cookie security attributes (SameSite=Strict CSRF mitigation)")
    class CookieSecurity {

        @Test
        @DisplayName("login should set refresh_token cookie with HttpOnly, SameSite=Strict, Path=/")
        void loginCookieShouldHaveSecurityAttributes() {
            when(authService.login(any(LoginRequest.class), any())).thenReturn(successResponse());

            var result = assertThat(mvc.post().uri("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validLoginJson()));
            result.hasStatusOk();
            result.headers().hasHeaderSatisfying("Set-Cookie", values ->
                assertThat(values).anyMatch(v -> v.contains("HttpOnly")));
            result.headers().hasHeaderSatisfying("Set-Cookie", values ->
                assertThat(values).anyMatch(v -> v.contains("SameSite=Strict")));
            result.headers().hasHeaderSatisfying("Set-Cookie", values ->
                assertThat(values).anyMatch(v -> v.contains("Path=/")));
        }

        @Test
        @DisplayName("refresh-token should set new refresh_token cookie with HttpOnly, SameSite=Strict")
        void refreshTokenCookieShouldHaveSecurityAttributes() {
            when(authService.refreshToken(anyString())).thenReturn(successResponse());

            var result = assertThat(mvc.post().uri("/auth/refresh-token")
                .cookie(new jakarta.servlet.http.Cookie("refresh_token", VALID_REFRESH_TOKEN)));
            result.hasStatusOk();
            result.headers().hasHeaderSatisfying("Set-Cookie", values ->
                assertThat(values).anyMatch(v -> v.contains("HttpOnly")));
            result.headers().hasHeaderSatisfying("Set-Cookie", values ->
                assertThat(values).anyMatch(v -> v.contains("SameSite=Strict")));
            result.headers().hasHeaderSatisfying("Set-Cookie", values ->
                assertThat(values).anyMatch(v -> v.contains("Path=/")));
        }

        @Test
        @DisplayName("logout should clear refresh_token cookie with max-age=0")
        void logoutCookieShouldBeCleared() {
            var result = assertThat(mvc.post().uri("/auth/logout"));
            result.hasStatus(HttpStatus.NO_CONTENT);
            result.headers().hasHeaderSatisfying("Set-Cookie", values ->
                assertThat(values).anyMatch(v -> v.contains("Max-Age=0")));
        }
    }
}
