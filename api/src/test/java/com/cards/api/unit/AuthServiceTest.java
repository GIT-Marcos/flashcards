package com.cards.api.unit;

import com.cards.api.config.properties.ApplicationProperties;
import com.cards.api.dto.SecurityUser;
import com.cards.api.dto.event.UserLoginEvent;
import com.cards.api.dto.request.ForgotPasswordRequest;
import com.cards.api.dto.request.LoginRequest;
import com.cards.api.dto.request.RegisterRequest;
import com.cards.api.dto.request.ResetPasswordRequest;
import com.cards.api.dto.response.AuthResponse;
import com.cards.api.dto.response.ForgotPasswordResponse;
import com.cards.api.dto.response.ResetPasswordResponse;
import com.cards.api.dto.response.SignupResponse;
import com.cards.api.entity.User;
import com.cards.api.exception.domain.*;
import com.cards.api.mapper.SecurityUserMapper;
import com.cards.api.repo.UserRepository;
import com.cards.api.service.AuthService;
import com.cards.api.service.JwtService;
import com.cards.api.service.notification.EmailService;
import com.cards.api.util.TokenType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService")
class AuthServiceTest {

    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtService jwtService;
    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private SecurityUserMapper securityUserMapper;
    @Mock
    private EmailService emailService;
    @Mock
    private ApplicationProperties properties;

    @Captor
    ArgumentCaptor<UserLoginEvent> eventCaptor;

    private AuthService authService;

    private static final String VALID_TOKEN = "access-token";
    private static final String VALID_REFRESH = "refresh-token";
    private static final String ENCODED_PASSWORD = "$2a$12$encodedPassword";
    private static final String RESET_TOKEN = "reset-jwt-token";
    private static final String NEW_PASSWORD = "NewStr0ngP@ss!";
    private static final String NEW_ENCODED = "$2a$12$newEncodedPassword";

    private static final SecurityUser MOCK_SECURITY_USER = new SecurityUser(1L, "testuser", "test@email.com", ENCODED_PASSWORD, "America/Buenos_Aires",
        List.of(new SimpleGrantedAuthority("ROLE_USER")));

    @BeforeEach
    void setUp() {
        var notifications = new ApplicationProperties.Notifications();
        notifications.setApiUrl("http://localhost:8080");
        notifications.setAppUrl("http://localhost:5173");
        lenient().when(properties.getNotifications()).thenReturn(notifications);

        authService = new AuthService(eventPublisher, userRepository, passwordEncoder,
            jwtService, authenticationManager, securityUserMapper, emailService, properties);
    }

    // ======================== HELPERS ========================

    private RegisterRequest validRegisterRequest() {
        return new RegisterRequest("newuser", "new@email.com", "Str0ngP@ss!", "America/Buenos_Aires");
    }

    private LoginRequest validLoginRequest() {
        return new LoginRequest("testuser", "Str0ngP@ss!");
    }

    private User persistedUser() {
        User user = User.builder()
            .username("testuser")
            .email("new@email.com")
            .passwordHash(ENCODED_PASSWORD)
            .zoneInfo("America/Buenos_Aires")
            .addRole(User.UserRole.ROLE_USER)
            .build();
        user.setId(1L);
        return user;
    }

    private ForgotPasswordRequest forgotPasswordRequest() {
        return new ForgotPasswordRequest("new@email.com");
    }

    private ResetPasswordRequest resetPasswordRequest() {
        return new ResetPasswordRequest(RESET_TOKEN, NEW_PASSWORD);
    }

    // ======================== SIGNUP ========================

    @Nested
    @DisplayName("signup")
    class Signup {

        @Test
        @DisplayName("should send verification email and return SignupResponse")
        void shouldSignupAndReturnResponse() {
            RegisterRequest request = validRegisterRequest();
            when(userRepository.existsByUsernameIgnoreCase("newuser")).thenReturn(false);
            when(userRepository.existsByEmailIgnoreCase("new@email.com")).thenReturn(false);
            when(passwordEncoder.encode("Str0ngP@ss!")).thenReturn(ENCODED_PASSWORD);
            when(jwtService.generateEmailVerificationToken("newuser", "new@email.com", ENCODED_PASSWORD, "America/Buenos_Aires"))
                .thenReturn("verification-jwt-token");

            SignupResponse response = authService.signup(request);

            assertThat(response.message()).contains("new@email.com");
            verify(emailService).sendVerificationEmail("new@email.com", "newuser",
                "http://localhost:8080/auth/confirm?token=verification-jwt-token");
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw InvalidTimeZoneException for invalid zone")
        void shouldThrowForInvalidTimeZone() {
            RegisterRequest request = new RegisterRequest("user", "a@b.com", "Str0ngP@ss!", "Invalid/Zone");

            assertThatThrownBy(() -> authService.signup(request))
                .isInstanceOf(InvalidTimeZoneException.class);
            verifyNoInteractions(userRepository);
        }

        @Test
        @DisplayName("should throw DuplicatedUsernameException when username taken")
        void shouldThrowForDuplicatedUsername() {
            RegisterRequest request = validRegisterRequest();
            when(userRepository.existsByUsernameIgnoreCase("newuser")).thenReturn(true);

            assertThatThrownBy(() -> authService.signup(request))
                .isInstanceOf(DuplicatedUsernameException.class);
            verify(emailService, never()).sendVerificationEmail(any(), any(), any());
        }

        @Test
        @DisplayName("should throw DuplicatedUserEmailException when email taken")
        void shouldThrowForDuplicatedEmail() {
            RegisterRequest request = validRegisterRequest();
            when(userRepository.existsByUsernameIgnoreCase("newuser")).thenReturn(false);
            when(userRepository.existsByEmailIgnoreCase("new@email.com")).thenReturn(true);

            assertThatThrownBy(() -> authService.signup(request))
                .isInstanceOf(DuplicatedUserEmailException.class);
            verify(emailService, never()).sendVerificationEmail(any(), any(), any());
        }
    }

    // ======================== LOGIN ========================

    @Nested
    @DisplayName("login")
    class Login {

        @Test
        @DisplayName("should login successfully, return tokens and publish event")
        void shouldLoginAndReturnTokens() {

            LoginRequest request = validLoginRequest();
            User user = persistedUser();

            when(userRepository.findByUsernameIgnoreCase("testuser"))
                .thenReturn(Optional.of(user));

            when(securityUserMapper.toSecurityUser(any(User.class))).thenReturn(MOCK_SECURITY_USER);
            when(jwtService.generateToken(any(SecurityUser.class)))
                .thenReturn(VALID_TOKEN);

            when(jwtService.generateRefreshToken(any(SecurityUser.class)))
                .thenReturn(VALID_REFRESH);

            AuthResponse response = authService.login(request, null);

            assertThat(response.accessToken()).isEqualTo(VALID_TOKEN);
            assertThat(response.refreshToken()).isEqualTo(VALID_REFRESH);
            assertThat(response.username()).isEqualTo("testuser");

            ArgumentCaptor<Authentication> authCaptor =
                ArgumentCaptor.forClass(Authentication.class);

            verify(authenticationManager).authenticate(authCaptor.capture());

            UsernamePasswordAuthenticationToken authToken =
                (UsernamePasswordAuthenticationToken) authCaptor.getValue();

            assertThat(authToken.getPrincipal()).isEqualTo("testuser");
            assertThat(authToken.getCredentials()).isEqualTo("Str0ngP@ss!");

            verify(eventPublisher).publishEvent(eventCaptor.capture());

            assertThat(eventCaptor.getValue().userId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("should propagate BadCredentialsException from AuthenticationManager")
        void shouldPropagateBadCredentials() {
            LoginRequest request = validLoginRequest();
            when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("Bad credentials"));

            assertThatThrownBy(() -> authService.login(request, null))
                .isInstanceOf(BadCredentialsException.class);
            verifyNoInteractions(eventPublisher);
        }

        @Test
        @DisplayName("should throw UsernameNotFoundException when user not found after auth")
        void shouldThrowWhenUserNotFoundAfterAuth() {
            LoginRequest request = validLoginRequest();
            when(userRepository.findByUsernameIgnoreCase("testuser")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.login(request, null))
                .isInstanceOf(UsernameNotFoundException.class);
        }
    }

    // ======================== REFRESH TOKEN ========================

    @Nested
    @DisplayName("refreshToken")
    class RefreshToken {

        @Test
        @DisplayName("should refresh tokens when valid refresh token is provided")
        void shouldRefreshTokens() {
            User user = persistedUser();
            io.jsonwebtoken.Claims mockClaims = io.jsonwebtoken.Jwts.claims()
                .add("tokenType", TokenType.REFRESH)
                .subject("testuser")
                .build();

            when(securityUserMapper.toSecurityUser(any(User.class))).thenReturn(MOCK_SECURITY_USER);
            when(jwtService.isRefreshToken(anyString())).thenReturn(true);
            when(jwtService.extractUsername(anyString())).thenReturn("testuser");
            when(userRepository.findByUsernameIgnoreCase("testuser")).thenReturn(Optional.of(user));
            when(jwtService.isTokenValid(anyString(), any(SecurityUser.class))).thenReturn(true);
            when(jwtService.generateToken(any(SecurityUser.class))).thenReturn("new-access");
            when(jwtService.generateRefreshToken(any(SecurityUser.class))).thenReturn("new-refresh");

            AuthResponse response = authService.refreshToken("valid-refresh-token");

            assertThat(response.accessToken()).isEqualTo("new-access");
            assertThat(response.refreshToken()).isEqualTo("new-refresh");
        }

        @Test
        @DisplayName("should throw InvalidRefreshTokenException when token is not a refresh token")
        void shouldThrowWhenTokenTypeIsNotRefresh() {
            when(jwtService.isRefreshToken(anyString())).thenReturn(false);

            assertThatThrownBy(() -> authService.refreshToken("access-token-as-refresh"))
                .isInstanceOf(InvalidRefreshTokenException.class);
        }

        @Test
        @DisplayName("should throw InvalidRefreshTokenException when user not found")
        void shouldThrowWhenUserNotFound() {
            when(jwtService.isRefreshToken(anyString())).thenReturn(true);
            when(jwtService.extractUsername(anyString())).thenReturn("ghost");
            when(userRepository.findByUsernameIgnoreCase("ghost")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.refreshToken("refresh-for-ghost"))
                .isInstanceOf(InvalidRefreshTokenException.class);
        }

        @Test
        @DisplayName("should throw RuntimeException when refresh token is invalid")
        void shouldThrowWhenRefreshTokenInvalid() {
            User user = persistedUser();

            when(securityUserMapper.toSecurityUser(any(User.class))).thenReturn(MOCK_SECURITY_USER);
            when(jwtService.isRefreshToken(anyString())).thenReturn(true);
            when(jwtService.extractUsername(anyString())).thenReturn("testuser");
            when(userRepository.findByUsernameIgnoreCase("testuser")).thenReturn(Optional.of(user));
            when(jwtService.isTokenValid(anyString(), any(SecurityUser.class))).thenReturn(false);

            assertThatThrownBy(() -> authService.refreshToken("invalid-refresh"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Invalid refresh token");
        }
    }

    // ======================== FORGOT PASSWORD ========================

    @Nested
    @DisplayName("forgotPassword")
    class ForgotPassword {

        @Test
        @DisplayName("should send reset email when user exists")
        void shouldSendResetEmailWhenUserExists() {
            ForgotPasswordRequest request = forgotPasswordRequest();
            User user = persistedUser();
            when(userRepository.findByEmailIgnoreCase("new@email.com")).thenReturn(Optional.of(user));
            when(jwtService.generatePasswordResetToken(1L, "new@email.com", ENCODED_PASSWORD))
                .thenReturn(RESET_TOKEN);

            ForgotPasswordResponse response = authService.forgotPassword(request);

            assertThat(response.message()).contains("password reset link has been sent");
            verify(emailService).sendPasswordResetEmail("new@email.com", "testuser",
                "http://localhost:5173/auth/reset-password?token=" + RESET_TOKEN);
        }

        @Test
        @DisplayName("should return generic message when user not found (avoid enumeration)")
        void shouldReturnGenericMessageWhenUserNotFound() {
            ForgotPasswordRequest request = forgotPasswordRequest();
            when(userRepository.findByEmailIgnoreCase("new@email.com")).thenReturn(Optional.empty());

            ForgotPasswordResponse response = authService.forgotPassword(request);

            assertThat(response.message()).contains("password reset link has been sent");
            verify(emailService, never()).sendPasswordResetEmail(any(), any(), any());
            verify(jwtService, never()).generatePasswordResetToken(any(), any(), any());
        }

        @Test
        @DisplayName("should propagate exception when email service fails")
        void shouldPropagateWhenEmailServiceFails() {
            ForgotPasswordRequest request = forgotPasswordRequest();
            User user = persistedUser();
            when(userRepository.findByEmailIgnoreCase("new@email.com")).thenReturn(Optional.of(user));
            when(jwtService.generatePasswordResetToken(any(), any(), any())).thenReturn(RESET_TOKEN);
            doThrow(new RuntimeException("Email failed")).when(emailService)
                .sendPasswordResetEmail(any(), any(), any());

            assertThatThrownBy(() -> authService.forgotPassword(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Email failed");
        }
    }

    // ======================== RESET PASSWORD ========================

    @Nested
    @DisplayName("resetPassword")
    class ResetPassword {

        @Test
        @DisplayName("should reset password successfully")
        void shouldResetPasswordSuccessfully() {
            ResetPasswordRequest request = resetPasswordRequest();
            User user = persistedUser();
            JwtService.ResetPasswordData data =
                new JwtService.ResetPasswordData(1L, "new@email.com", ENCODED_PASSWORD);

            when(jwtService.isResetPasswordToken(RESET_TOKEN)).thenReturn(true);
            when(jwtService.extractResetPasswordData(RESET_TOKEN)).thenReturn(data);
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(passwordEncoder.encode(NEW_PASSWORD)).thenReturn(NEW_ENCODED);

            ResetPasswordResponse response = authService.resetPassword(request);

            assertThat(response.message()).contains("successfully reset");
            verify(userRepository).save(user);
            assertThat(user.getPasswordHash()).isEqualTo(NEW_ENCODED);
        }

        @Test
        @DisplayName("should throw when token type is not PASSWORD_RESET")
        void shouldThrowWhenTokenTypeIsNotReset() {
            ResetPasswordRequest request = resetPasswordRequest();
            when(jwtService.isResetPasswordToken(RESET_TOKEN)).thenReturn(false);

            assertThatThrownBy(() -> authService.resetPassword(request))
                .isInstanceOf(InvalidResetPasswordTokenException.class);
        }

        @Test
        @DisplayName("should throw when token extraction fails")
        void shouldThrowWhenExtractDataFails() {
            ResetPasswordRequest request = resetPasswordRequest();
            when(jwtService.isResetPasswordToken(RESET_TOKEN)).thenReturn(true);
            when(jwtService.extractResetPasswordData(RESET_TOKEN)).thenThrow(new RuntimeException());

            assertThatThrownBy(() -> authService.resetPassword(request))
                .isInstanceOf(InvalidResetPasswordTokenException.class);
        }

        @Test
        @DisplayName("should throw when user not found")
        void shouldThrowWhenUserNotFound() {
            ResetPasswordRequest request = resetPasswordRequest();
            JwtService.ResetPasswordData data =
                new JwtService.ResetPasswordData(999L, "ghost@email.com", "hash");

            when(jwtService.isResetPasswordToken(RESET_TOKEN)).thenReturn(true);
            when(jwtService.extractResetPasswordData(RESET_TOKEN)).thenReturn(data);
            when(userRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.resetPassword(request))
                .isInstanceOf(InvalidResetPasswordTokenException.class);
        }

        @Test
        @DisplayName("should throw when password hash has changed (token already used)")
        void shouldThrowWhenPasswordHashMismatch() {
            ResetPasswordRequest request = resetPasswordRequest();
            User user = persistedUser();
            user.setPasswordHash("$2a$12$differentHash");
            JwtService.ResetPasswordData data =
                new JwtService.ResetPasswordData(1L, "new@email.com", ENCODED_PASSWORD);

            when(jwtService.isResetPasswordToken(RESET_TOKEN)).thenReturn(true);
            when(jwtService.extractResetPasswordData(RESET_TOKEN)).thenReturn(data);
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> authService.resetPassword(request))
                .isInstanceOf(InvalidResetPasswordTokenException.class)
                .hasMessageContaining("already been used");
        }
    }
}
