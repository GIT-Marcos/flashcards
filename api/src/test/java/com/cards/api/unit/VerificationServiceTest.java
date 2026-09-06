package com.cards.api.unit;

import com.cards.api.entity.User;
import com.cards.api.exception.domain.DuplicatedUserEmailException;
import com.cards.api.exception.domain.DuplicatedUsernameException;
import com.cards.api.exception.domain.InvalidEmailVerificationException;
import com.cards.api.repo.UserRepository;
import com.cards.api.service.JwtService;
import com.cards.api.service.VerificationService;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("VerificationService")
class VerificationServiceTest {

    @Mock
    private JwtService jwtService;
    @Mock
    private UserRepository userRepository;

    @Captor
    ArgumentCaptor<User> userCaptor;

    private VerificationService verificationService;

    private static final String VALID_TOKEN = "valid-verification-jwt";
    private static final String USERNAME = "newuser";
    private static final String EMAIL = "new@email.com";
    private static final String PASSWORD_HASH = "$2a$12$encodedPassword";
    private static final String ZONE = "America/Buenos_Aires";

    @BeforeEach
    void setUp() {
        verificationService = new VerificationService(jwtService, userRepository);
    }

    // ======================== VALIDATE TOKEN STRUCTURE ========================

    @Nested
    @DisplayName("validateTokenStructure")
    class ValidateTokenStructure {

        @Test
        @DisplayName("should succeed when token is valid")
        void shouldSucceedForValidToken() {
            verificationService.validateTokenStructure(VALID_TOKEN);

            verify(jwtService).extractAllClaims(VALID_TOKEN);
        }

        @Test
        @DisplayName("should propagate exception when token is invalid")
        void shouldPropagateForInvalidToken() {
            doThrow(new RuntimeException("Invalid JWT")).when(jwtService).extractAllClaims(VALID_TOKEN);

            assertThatThrownBy(() -> verificationService.validateTokenStructure(VALID_TOKEN))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Invalid JWT");
        }
    }

    // ======================== CONFIRM EMAIL ========================

    @Nested
    @DisplayName("confirmEmail")
    class ConfirmEmail {

        private JwtService.VerifyData verifyData;

        @BeforeEach
        void setUp() {
            verifyData = new JwtService.VerifyData(USERNAME, EMAIL, PASSWORD_HASH, ZONE);
        }

        @Test
        @DisplayName("should create user when token is valid and data is unique")
        void shouldCreateUser() {
            when(jwtService.extractVerificationData(VALID_TOKEN)).thenReturn(verifyData);
            when(jwtService.isTokenType(VALID_TOKEN, TokenType.VERIFY_EMAIL)).thenReturn(true);
            when(userRepository.existsByUsernameIgnoreCase(USERNAME)).thenReturn(false);
            when(userRepository.existsByEmailIgnoreCase(EMAIL)).thenReturn(false);
            when(userRepository.save(any())).thenAnswer(invocation -> {
                User u = invocation.getArgument(0);
                u.setId(1L);
                return u;
            });

            verificationService.confirmEmail(VALID_TOKEN);

            verify(userRepository).save(userCaptor.capture());
            User saved = userCaptor.getValue();
            assertThat(saved.getUsername()).isEqualTo(USERNAME);
            assertThat(saved.getEmail()).isEqualTo(EMAIL);
            assertThat(saved.getPasswordHash()).isEqualTo(PASSWORD_HASH);
            assertThat(saved.getZoneInfo()).isEqualTo(ZONE);
            assertThat(saved.getRoles()).contains(User.UserRole.ROLE_USER);
        }

        @Test
        @DisplayName("should throw InvalidEmailVerificationException when extractVerificationData fails")
        void shouldThrowForInvalidToken() {
            when(jwtService.extractVerificationData(VALID_TOKEN))
                .thenThrow(new RuntimeException("expired or malformed"));

            assertThatThrownBy(() -> verificationService.confirmEmail(VALID_TOKEN))
                .isInstanceOf(InvalidEmailVerificationException.class);
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw InvalidEmailVerificationException when token type is not VERIFY_EMAIL")
        void shouldThrowForWrongTokenType() {
            when(jwtService.extractVerificationData(VALID_TOKEN)).thenReturn(verifyData);
            when(jwtService.isTokenType(VALID_TOKEN, TokenType.VERIFY_EMAIL)).thenReturn(false);

            assertThatThrownBy(() -> verificationService.confirmEmail(VALID_TOKEN))
                .isInstanceOf(InvalidEmailVerificationException.class);
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw DuplicatedUsernameException when username already exists")
        void shouldThrowForDuplicatedUsername() {
            when(jwtService.extractVerificationData(VALID_TOKEN)).thenReturn(verifyData);
            when(jwtService.isTokenType(VALID_TOKEN, TokenType.VERIFY_EMAIL)).thenReturn(true);
            when(userRepository.existsByUsernameIgnoreCase(USERNAME)).thenReturn(true);

            assertThatThrownBy(() -> verificationService.confirmEmail(VALID_TOKEN))
                .isInstanceOf(DuplicatedUsernameException.class);
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw DuplicatedUserEmailException when email already exists")
        void shouldThrowForDuplicatedEmail() {
            when(jwtService.extractVerificationData(VALID_TOKEN)).thenReturn(verifyData);
            when(jwtService.isTokenType(VALID_TOKEN, TokenType.VERIFY_EMAIL)).thenReturn(true);
            when(userRepository.existsByUsernameIgnoreCase(USERNAME)).thenReturn(false);
            when(userRepository.existsByEmailIgnoreCase(EMAIL)).thenReturn(true);

            assertThatThrownBy(() -> verificationService.confirmEmail(VALID_TOKEN))
                .isInstanceOf(DuplicatedUserEmailException.class);
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw InvalidEmailVerificationException when save fails")
        void shouldThrowWhenSaveFails() {
            when(jwtService.extractVerificationData(VALID_TOKEN)).thenReturn(verifyData);
            when(jwtService.isTokenType(VALID_TOKEN, TokenType.VERIFY_EMAIL)).thenReturn(true);
            when(userRepository.existsByUsernameIgnoreCase(USERNAME)).thenReturn(false);
            when(userRepository.existsByEmailIgnoreCase(EMAIL)).thenReturn(false);
            when(userRepository.save(any())).thenThrow(new RuntimeException("DB constraint violation"));

            assertThatThrownBy(() -> verificationService.confirmEmail(VALID_TOKEN))
                .isInstanceOf(InvalidEmailVerificationException.class);
        }

    }
}
