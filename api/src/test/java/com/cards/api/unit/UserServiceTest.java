package com.cards.api.unit;

import com.cards.api.dto.request.PatchUserRequest;
import com.cards.api.dto.response.UserResponse;
import com.cards.api.entity.User;
import com.cards.api.exception.ResourceNotFoundException;
import com.cards.api.exception.domain.DuplicatedUserEmailException;
import com.cards.api.exception.domain.DuplicatedUsernameException;
import com.cards.api.mapper.UserMapper;
import com.cards.api.repo.UserRepository;
import com.cards.api.service.UserService;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.sql.SQLException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserService")
class UserServiceTest {

    @Mock
    private UserRepository userRepo;
    @Mock
    private UserMapper mapper;
    @Mock
    private PasswordEncoder passwordEncoder;

    private UserService userService;

    private static final Long USER_ID = 1L;
    private static final String CURRENT_HASH = "$2a$12$currentHashedPassword";

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepo, mapper, passwordEncoder);
    }

    // ======================== HELPERS ========================

    private User defaultUser() {
        User user = User.builder()
            .username("testuser")
            .email("test@email.com")
            .passwordHash(CURRENT_HASH)
            .zoneInfo("UTC")
            .addRole(User.UserRole.ROLE_USER)
            .build();
        user.setId(USER_ID);
        return user;
    }

    private UserResponse toResponse(User u) {
        return new UserResponse(
            u.getId(),
            u.getUsername(),
            u.getEmail(),
            u.getZoneInfo(),
            u.getCreatedAt(),
            u.getLastLogin(),
            u.getLastNotificationSent(),
            u.getSessionThreshold(),
            u.getStartOfDay(),
            u.isNotificationsEnabled(),
            u.getRoles().stream().map(Enum::toString).collect(java.util.stream.Collectors.toSet())
        );
    }

    private UserResponse defaultUserResponse() {
        return toResponse(defaultUser());
    }

    // ======================== GET CURRENT ========================

    @Nested
    @DisplayName("getCurrent")
    class GetCurrent {

        @Test
        @DisplayName("should return user response")
        void shouldReturnUser() {
            when(userRepo.findById(USER_ID)).thenReturn(Optional.of(defaultUser()));
            when(mapper.toResponse(defaultUser())).thenReturn(defaultUserResponse());

            UserResponse response = userService.getCurrent(USER_ID);

            assertThat(response.username()).isEqualTo("testuser");
            assertThat(response.email()).isEqualTo("test@email.com");
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when user not found")
        void shouldThrowWhenNotFound() {
            when(userRepo.findById(USER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.getCurrent(USER_ID))
                .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ======================== PATCH ========================

    @Nested
    @DisplayName("patch")
    class Patch {

        @Test
        @DisplayName("should update username successfully")
        void shouldUpdateUsername() {
            PatchUserRequest request = new PatchUserRequest("newname", null, null, null, null, null, null);
            User user = defaultUser();
            UserResponse expected = new UserResponse(
                user.getId(), user.getUsername(), user.getEmail(), user.getZoneInfo(),
                user.getCreatedAt(), user.getLastLogin(), user.getLastNotificationSent(),
                user.getSessionThreshold(), user.getStartOfDay(),
                user.isNotificationsEnabled(),
                user.getRoles().stream().map(Enum::toString).collect(java.util.stream.Collectors.toSet())
            );

            when(userRepo.findById(USER_ID)).thenReturn(Optional.of(user));
            when(userRepo.existsByUsernameIgnoreCase("newname")).thenReturn(false);
            when(mapper.patchEntity(user, request)).thenReturn(user);
            when(userRepo.save(user)).thenReturn(user);
            when(mapper.toResponse(user)).thenReturn(expected);

            UserResponse response = userService.patch(USER_ID, request);

            assertThat(response.username()).isEqualTo("testuser");
            verify(userRepo).save(user);
        }

        @Test
        @DisplayName("should update email successfully")
        void shouldUpdateEmail() {
            PatchUserRequest request = new PatchUserRequest(null, "new@email.com", null, null, null, null, null);
            User user = defaultUser();

            when(userRepo.findById(USER_ID)).thenReturn(Optional.of(user));
            when(userRepo.existsByEmailIgnoreCase("new@email.com")).thenReturn(false);
            when(mapper.patchEntity(user, request)).thenReturn(user);
            when(userRepo.save(user)).thenReturn(user);
            when(mapper.toResponse(user)).thenReturn(toResponse(user));

            userService.patch(USER_ID, request);

            verify(userRepo).save(user);
        }

        @Test
        @DisplayName("should update password when current password matches")
        void shouldUpdatePassword() {
            PatchUserRequest request = new PatchUserRequest(null, null, null, null, null, "currentPwd", "N3wP@ssw0rd!");
            User user = defaultUser();

            when(userRepo.findById(USER_ID)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("currentPwd", CURRENT_HASH)).thenReturn(true);
            when(passwordEncoder.encode("N3wP@ssw0rd!")).thenReturn("newEncodedHash");
            when(mapper.patchEntity(user, request)).thenReturn(user);
            when(userRepo.save(user)).thenReturn(user);
            when(mapper.toResponse(user)).thenReturn(toResponse(user));

            userService.patch(USER_ID, request);

            assertThat(user.getPasswordHash()).isEqualTo("newEncodedHash");
        }

        @Test
        @DisplayName("should throw BadCredentialsException when changing password without current")
        void shouldThrowWhenPasswordWithoutCurrent() {
            PatchUserRequest request = new PatchUserRequest(null, null, null, null, null, null, "N3wP@ssw0rd!");

            when(userRepo.findById(USER_ID)).thenReturn(Optional.of(defaultUser()));

            assertThatThrownBy(() -> userService.patch(USER_ID, request))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("The current password is required");

            verify(passwordEncoder, never()).encode(anyString());
        }

        @Test
        @DisplayName("should throw BadCredentialsException when current password is wrong")
        void shouldThrowWhenCurrentPasswordWrong() {
            PatchUserRequest request = new PatchUserRequest(null, null, null, null, null, "wrongPwd", "N3wP@ssw0rd!");

            when(userRepo.findById(USER_ID)).thenReturn(Optional.of(defaultUser()));
            when(passwordEncoder.matches("wrongPwd", CURRENT_HASH)).thenReturn(false);

            assertThatThrownBy(() -> userService.patch(USER_ID, request))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("The current password does not match");

            verify(passwordEncoder, never()).encode(anyString());
        }

        @Test
        @DisplayName("should throw DuplicatedUsernameException when username taken")
        void shouldThrowForDuplicatedUsername() {
            PatchUserRequest request = new PatchUserRequest("taken", null, null, null, null, null, null);

            when(userRepo.findById(USER_ID)).thenReturn(Optional.of(defaultUser()));
            when(userRepo.existsByUsernameIgnoreCase("taken")).thenReturn(true);

            assertThatThrownBy(() -> userService.patch(USER_ID, request))
                .isInstanceOf(DuplicatedUsernameException.class);
        }

        @Test
        @DisplayName("should throw DuplicatedUserEmailException when email taken")
        void shouldThrowForDuplicatedEmail() {
            PatchUserRequest request = new PatchUserRequest(null, "taken@email.com", null, null, null, null, null);

            when(userRepo.findById(USER_ID)).thenReturn(Optional.of(defaultUser()));
            when(userRepo.existsByEmailIgnoreCase("taken@email.com")).thenReturn(true);

            assertThatThrownBy(() -> userService.patch(USER_ID, request))
                .isInstanceOf(DuplicatedUserEmailException.class);
        }

        @Test
        @DisplayName("should throw DuplicatedUsernameException on username unique violation")
        void shouldCatchDataIntegrityAndThrowDuplicatedUsername() {
            PatchUserRequest request = new PatchUserRequest("new", null, null, null, null, null, null);
            User user = defaultUser();

            when(userRepo.findById(USER_ID)).thenReturn(Optional.of(user));
            when(userRepo.existsByUsernameIgnoreCase("new")).thenReturn(false);
            when(mapper.patchEntity(user, request)).thenReturn(user);

            var sqlEx = new SQLException("unique violation", "23505");
            var constraintEx = new ConstraintViolationException("unique violation", sqlEx, "uk_users_username_lower");
            when(userRepo.save(user)).thenThrow(new DataIntegrityViolationException("unique", constraintEx));

            assertThatThrownBy(() -> userService.patch(USER_ID, request))
                .isInstanceOf(DuplicatedUsernameException.class);
        }

        @Test
        @DisplayName("should throw DuplicatedUserEmailException on email unique violation")
        void shouldCatchDataIntegrityAndThrowDuplicatedEmail() {
            PatchUserRequest request = new PatchUserRequest(null, "new@email.com", null, null, null, null, null);
            User user = defaultUser();

            when(userRepo.findById(USER_ID)).thenReturn(Optional.of(user));
            when(userRepo.existsByEmailIgnoreCase("new@email.com")).thenReturn(false);
            when(mapper.patchEntity(user, request)).thenReturn(user);

            var sqlEx = new SQLException("unique violation", "23505");
            var constraintEx = new ConstraintViolationException("unique violation", sqlEx, "uk_users_email_lower");
            when(userRepo.save(user)).thenThrow(new DataIntegrityViolationException("unique", constraintEx));

            assertThatThrownBy(() -> userService.patch(USER_ID, request))
                .isInstanceOf(DuplicatedUserEmailException.class);
        }

        @Test
        @DisplayName("should throw BadCredentialsException on unknown constraint violation")
        void shouldCatchDataIntegrityAndThrowBadCredentialsFallback() {
            PatchUserRequest request = new PatchUserRequest("new", null, null, null, null, null, null);
            User user = defaultUser();

            when(userRepo.findById(USER_ID)).thenReturn(Optional.of(user));
            when(userRepo.existsByUsernameIgnoreCase("new")).thenReturn(false);
            when(mapper.patchEntity(user, request)).thenReturn(user);

            var sqlEx = new SQLException("unique violation", "23505");
            var constraintEx = new ConstraintViolationException("unique violation", sqlEx, "uk_unknown");
            when(userRepo.save(user)).thenThrow(new DataIntegrityViolationException("unique", constraintEx));

            assertThatThrownBy(() -> userService.patch(USER_ID, request))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Error while saving - Data integrity violation");
        }

        @Test
        @DisplayName("should allow patching with same username without duplication check")
        void shouldAllowSameUsernameWithoutDupCheck() {
            PatchUserRequest request = new PatchUserRequest("testuser", null, null, null, null, null, null);
            User user = defaultUser();

            when(userRepo.findById(USER_ID)).thenReturn(Optional.of(user));
            when(mapper.patchEntity(user, request)).thenReturn(user);
            when(userRepo.save(user)).thenReturn(user);
            when(mapper.toResponse(user)).thenReturn(toResponse(user));

            userService.patch(USER_ID, request);

            verify(userRepo, never()).existsByUsernameIgnoreCase(anyString());
        }

        @Test
        @DisplayName("should allow patching with same email without duplication check")
        void shouldAllowSameEmailWithoutDupCheck() {
            PatchUserRequest request = new PatchUserRequest(null, "test@email.com", null, null, null, null, null);
            User user = defaultUser();

            when(userRepo.findById(USER_ID)).thenReturn(Optional.of(user));
            when(mapper.patchEntity(user, request)).thenReturn(user);
            when(userRepo.save(user)).thenReturn(user);
            when(mapper.toResponse(user)).thenReturn(toResponse(user));

            userService.patch(USER_ID, request);

            verify(userRepo, never()).existsByEmailIgnoreCase(anyString());
        }

        @Test
        @DisplayName("should update notificationsEnabled")
        void shouldUpdateNotificationsEnabled() {
            PatchUserRequest request = new PatchUserRequest("testuser", "test@email.com", 30, 6, false, null, null);
            User user = defaultUser();

            when(userRepo.findById(USER_ID)).thenReturn(Optional.of(user));
            when(mapper.patchEntity(user, request)).thenAnswer(invocation -> {
                user.setNotificationsEnabled(false);
                return user;
            });
            when(userRepo.save(user)).thenReturn(user);
            when(mapper.toResponse(user)).thenReturn(toResponse(user));

            userService.patch(USER_ID, request);

            assertThat(user.isNotificationsEnabled()).isFalse();
        }

        @Test
        @DisplayName("should NOT change notificationsEnabled when null")
        void shouldNotChangeNotificationsEnabledWhenNull() {
            PatchUserRequest request = new PatchUserRequest("testuser", "test@email.com", 30, 6, null, null, null);
            User user = defaultUser();

            when(userRepo.findById(USER_ID)).thenReturn(Optional.of(user));
            when(mapper.patchEntity(user, request)).thenAnswer(invocation -> {
                return user;
            });
            when(userRepo.save(user)).thenReturn(user);
            when(mapper.toResponse(user)).thenReturn(toResponse(user));

            userService.patch(USER_ID, request);

            assertThat(user.isNotificationsEnabled()).isTrue();
        }
    }

    // ======================== DELETE ========================

    @Nested
    @DisplayName("deleteUser")
    class DeleteUser {

        @Test
        @DisplayName("should delete user when user exists")
        void shouldDeleteWhenUserExists() {
            when(userRepo.existsById(USER_ID)).thenReturn(true);

            userService.deleteUser(USER_ID);

            verify(userRepo).deleteById(USER_ID);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when user does not exist")
        void shouldThrowWhenUserDoesNotExist() {
            when(userRepo.existsById(USER_ID)).thenReturn(false);

            assertThatThrownBy(() -> userService.deleteUser(USER_ID))
                .isInstanceOf(ResourceNotFoundException.class);

            verify(userRepo, never()).deleteById(any());
        }
    }

    // ======================== UPDATE NOTIFICATION TIMESTAMP ========================

    @Nested
    @DisplayName("updateUserNotificationTimestamp")
    class UpdateNotificationTimestamp {

        @Test
        @DisplayName("should update timestamp when user exists")
        void shouldUpdateWhenUserExists() {
            User user = defaultUser();
            java.time.Instant now = java.time.Instant.now();

            when(userRepo.findById(USER_ID)).thenReturn(Optional.of(user));

            userService.updateUserNotificationTimestamp(USER_ID, now);

            assertThat(user.getLastNotificationSent()).isEqualTo(now);
            verify(userRepo).save(user);
        }

        @Test
        @DisplayName("should silently skip when user does not exist")
        void shouldSkipWhenUserNotFound() {
            when(userRepo.findById(USER_ID)).thenReturn(Optional.empty());

            userService.updateUserNotificationTimestamp(USER_ID, java.time.Instant.now());

            verify(userRepo, never()).save(any());
        }
    }
}
