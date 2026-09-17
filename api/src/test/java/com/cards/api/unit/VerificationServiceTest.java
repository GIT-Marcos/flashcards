package com.cards.api.unit;

import com.cards.api.entity.PendingRegistration;
import com.cards.api.entity.User;
import com.cards.api.exception.domain.DuplicatedUserEmailException;
import com.cards.api.exception.domain.DuplicatedUsernameException;
import com.cards.api.exception.domain.InvalidEmailVerificationException;
import com.cards.api.repo.UserRepository;
import com.cards.api.service.PendingRegistrationService;
import com.cards.api.service.VerificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("VerificationService")
class VerificationServiceTest {

    @Mock
    private PendingRegistrationService pendingRegistrationService;
    @Mock
    private UserRepository userRepository;

    @Captor
    ArgumentCaptor<User> userCaptor;

    private VerificationService verificationService;

    private static final String VALID_TOKEN = "valid-opaque-token";
    private static final String USERNAME = "newuser";
    private static final String EMAIL = "new@email.com";
    private static final String PASSWORD_HASH = "$2a$12$encodedPassword";
    private static final String ZONE = "America/Buenos_Aires";

    @BeforeEach
    void setUp() {
        verificationService = new VerificationService(pendingRegistrationService, userRepository);
    }

    // ======================== VALIDATE TOKEN STRUCTURE ========================

    @Nested
    @DisplayName("validateTokenStructure")
    class ValidateTokenStructure {

        @Test
        @DisplayName("should succeed when token is valid")
        void shouldSucceedForValidToken() {
            when(pendingRegistrationService.findValidByToken(VALID_TOKEN))
                    .thenReturn(Optional.of(pendingRegistration()));

            verificationService.validateTokenStructure(VALID_TOKEN);

            verify(pendingRegistrationService).findValidByToken(VALID_TOKEN);
        }

        @Test
        @DisplayName("should throw InvalidEmailVerificationException when token is invalid or expired")
        void shouldThrowForInvalidToken() {
            when(pendingRegistrationService.findValidByToken(VALID_TOKEN))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> verificationService.validateTokenStructure(VALID_TOKEN))
                    .isInstanceOf(InvalidEmailVerificationException.class);
        }
    }

    // ======================== CONFIRM EMAIL ========================

    @Nested
    @DisplayName("confirmEmail")
    class ConfirmEmail {

        @Test
        @DisplayName("should create user when token is valid and data is unique")
        void shouldCreateUser() {
            PendingRegistration pending = pendingRegistration();
            when(pendingRegistrationService.findValidByToken(VALID_TOKEN)).thenReturn(Optional.of(pending));
            when(userRepository.existsByUsernameIgnoreCase(USERNAME)).thenReturn(false);
            when(userRepository.existsByEmailIgnoreCase(EMAIL)).thenReturn(false);
            when(userRepository.save(any())).thenAnswer(invocation -> {
                User u = invocation.getArgument(0);
                u.setId(1L);
                return u;
            });

            verificationService.confirmEmail(VALID_TOKEN);

            verify(userRepository).save(userCaptor.capture());
            verify(pendingRegistrationService).delete(pending);
            User saved = userCaptor.getValue();
            assertThat(saved.getUsername()).isEqualTo(USERNAME);
            assertThat(saved.getEmail()).isEqualTo(EMAIL);
            assertThat(saved.getPasswordHash()).isEqualTo(PASSWORD_HASH);
            assertThat(saved.getZoneInfo()).isEqualTo(ZONE);
            assertThat(saved.getRoles()).contains(User.UserRole.ROLE_USER);
        }

        @Test
        @DisplayName("should throw InvalidEmailVerificationException when token is not found")
        void shouldThrowForInvalidToken() {
            when(pendingRegistrationService.findValidByToken(VALID_TOKEN))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> verificationService.confirmEmail(VALID_TOKEN))
                    .isInstanceOf(InvalidEmailVerificationException.class);
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw DuplicatedUsernameException when username already exists")
        void shouldThrowForDuplicatedUsername() {
            when(pendingRegistrationService.findValidByToken(VALID_TOKEN))
                    .thenReturn(Optional.of(pendingRegistration()));
            when(userRepository.existsByUsernameIgnoreCase(USERNAME)).thenReturn(true);

            assertThatThrownBy(() -> verificationService.confirmEmail(VALID_TOKEN))
                    .isInstanceOf(DuplicatedUsernameException.class);
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw DuplicatedUserEmailException when email already exists")
        void shouldThrowForDuplicatedEmail() {
            when(pendingRegistrationService.findValidByToken(VALID_TOKEN))
                    .thenReturn(Optional.of(pendingRegistration()));
            when(userRepository.existsByUsernameIgnoreCase(USERNAME)).thenReturn(false);
            when(userRepository.existsByEmailIgnoreCase(EMAIL)).thenReturn(true);

            assertThatThrownBy(() -> verificationService.confirmEmail(VALID_TOKEN))
                    .isInstanceOf(DuplicatedUserEmailException.class);
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw InvalidEmailVerificationException when save fails")
        void shouldThrowWhenSaveFails() {
            when(pendingRegistrationService.findValidByToken(VALID_TOKEN))
                    .thenReturn(Optional.of(pendingRegistration()));
            when(userRepository.existsByUsernameIgnoreCase(USERNAME)).thenReturn(false);
            when(userRepository.existsByEmailIgnoreCase(EMAIL)).thenReturn(false);
            when(userRepository.save(any())).thenThrow(new RuntimeException("DB constraint violation"));

            assertThatThrownBy(() -> verificationService.confirmEmail(VALID_TOKEN))
                    .isInstanceOf(InvalidEmailVerificationException.class);
        }

    }

    private PendingRegistration pendingRegistration() {
        return PendingRegistration.builder()
                .username(USERNAME)
                .email(EMAIL)
                .passwordHash(PASSWORD_HASH)
                .zoneInfo(ZONE)
                .tokenHash("a".repeat(64))
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
    }
}
