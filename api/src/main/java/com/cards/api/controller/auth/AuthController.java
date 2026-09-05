package com.cards.api.controller.auth;

import com.cards.api.config.properties.ApplicationProperties;
import com.cards.api.dto.request.ForgotPasswordRequest;
import com.cards.api.dto.request.LoginRequest;
import com.cards.api.dto.request.RegisterRequest;
import com.cards.api.dto.request.ResetPasswordRequest;
import com.cards.api.dto.response.AuthResponse;
import com.cards.api.dto.response.ForgotPasswordResponse;
import com.cards.api.dto.response.ResetPasswordResponse;
import com.cards.api.dto.response.SignupResponse;
import com.cards.api.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Authentication", description = "Register, login, refresh tokens, and logout")
@SecurityRequirements
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;
    private final ApplicationProperties properties;
    private static final String REFRESH_TOKEN_COOKIE_NAME = "refresh_token";
    private static final int COOKIE_MAX_AGE = 7 * 24 * 60 * 60; // 7 days

    public AuthController(AuthService authService, ApplicationProperties properties) {
        this.authService = authService;
        this.properties = properties;
    }

    @Operation(summary = "Sign up", description = "Initiates account creation. Validates data and sends a verification email. The account is created only after email confirmation.")
    @ApiResponses({
        @ApiResponse(responseCode = "202", description = "Verification email sent"),
        @ApiResponse(responseCode = "400", description = "Validation error — ProblemDetail"),
        @ApiResponse(responseCode = "429", description = "Too many signup attempts — ProblemDetail")
    })
    @PostMapping("/signup")
    public ResponseEntity<SignupResponse> signup(
        @RequestBody @Valid RegisterRequest request
    ) {
        SignupResponse response = authService.signup(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @Operation(summary = "Forgot password", description = "Sends a password reset email if the account exists. Always returns 202 to avoid user enumeration.")
    @ApiResponses({
        @ApiResponse(responseCode = "202", description = "Reset email sent (or user not found — same response)"),
        @ApiResponse(responseCode = "400", description = "Validation error — ProblemDetail"),
        @ApiResponse(responseCode = "429", description = "Too many requests — ProblemDetail")
    })
    @PostMapping("/forgot-password")
    public ResponseEntity<ForgotPasswordResponse> forgotPassword(
        @RequestBody @Valid ForgotPasswordRequest request
    ) {
        ForgotPasswordResponse response = authService.forgotPassword(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @Operation(summary = "Reset password", description = "Validates the reset token and updates the password. The token becomes invalid after use.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Password reset successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid/expired token or validation error — ProblemDetail"),
        @ApiResponse(responseCode = "429", description = "Too many requests — ProblemDetail")
    })
    @PostMapping("/reset-password")
    public ResponseEntity<ResetPasswordResponse> resetPassword(
        @RequestBody @Valid ResetPasswordRequest request
    ) {
        ResetPasswordResponse response = authService.resetPassword(request);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Log in", description = "Authenticates with username and password, returns JWT tokens. The refresh token is set as an httpOnly cookie.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Login successful"),
        @ApiResponse(responseCode = "400", description = "Validation error — ProblemDetail"),
        @ApiResponse(responseCode = "429", description = "Too many login attempts — ProblemDetail")
    })
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
        @RequestBody @Valid LoginRequest request,
        @RequestHeader(value = "Time-Zone", required = false) String zoneInfo,
        HttpServletResponse httpResponse
    ) {
        AuthResponse authResponse = authService.login(request, zoneInfo);
        ResponseCookie refreshTokenCookie = createCookie(authResponse.refreshToken(), COOKIE_MAX_AGE);
        httpResponse.addHeader(HttpHeaders.SET_COOKIE, refreshTokenCookie.toString());

        return ResponseEntity.ok(authResponse);
    }


    @Operation(summary = "Refresh access token", description = "Exchanges a valid refresh token (from cookie) for a new access token and refresh token pair.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Token refreshed successfully"),
        @ApiResponse(responseCode = "400", description = "Missing or invalid refresh token cookie — ProblemDetail"),
        @ApiResponse(responseCode = "429", description = "Too many refresh attempts — ProblemDetail")
    })
    @PostMapping("/refresh-token")
    public ResponseEntity<AuthResponse> refreshToken(
        @CookieValue(name = REFRESH_TOKEN_COOKIE_NAME) String refreshToken,
        HttpServletResponse response
    ) {
        AuthResponse authResponse = authService.refreshToken(refreshToken);
        ResponseCookie responseCookie = createCookie(authResponse.refreshToken(), COOKIE_MAX_AGE);
        response.addHeader(HttpHeaders.SET_COOKIE, responseCookie.toString());

        return ResponseEntity.ok(authResponse);
    }


    @Operation(summary = "Log out", description = "Soft logout. Clears the refresh token cookie.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Logout successful, cookie cleared")
    })
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletResponse response) {
        ResponseCookie responseCookie = createCookie("", 0);
        response.addHeader(HttpHeaders.SET_COOKIE, responseCookie.toString());

        return ResponseEntity.noContent().build();
    }

    private ResponseCookie createCookie(String token, int maxAge) {
        var security = properties.getSecurity();
        return ResponseCookie.from(REFRESH_TOKEN_COOKIE_NAME, token)
            .httpOnly(true)
            .secure(security.isSecureCookie())
            .path("/")
            .maxAge(maxAge)
            .sameSite(security.getSameSite())
            .build();
    }
}
