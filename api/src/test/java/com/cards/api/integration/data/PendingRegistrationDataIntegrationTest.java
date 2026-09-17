package com.cards.api.integration.data;

import com.cards.api.config.AuditConfig;
import com.cards.api.entity.PendingRegistration;
import com.cards.api.infraestructure.config.JpaTestConfig;
import com.cards.api.integration.TestcontainersConfig;
import com.cards.api.repo.PendingRegistrationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import({TestcontainersConfig.class, AuditConfig.class, JpaTestConfig.class})
@DisplayName("PendingRegistration")
class PendingRegistrationDataIntegrationTest {

    private static final String PASSWORD_HASH = "$2a$12$encodedPassword";
    private static final String ZONE = "America/Buenos_Aires";

    @Autowired
    private PendingRegistrationRepository pendingRegistrationRepository;

    @Autowired
    private TestEntityManager entityManager;

    @BeforeEach
    void setUp() {
        pendingRegistrationRepository.deleteAllInBatch();
    }

    // ========================================================================
    // Helper
    // ========================================================================

    private PendingRegistration pendingRow(String username, String email, String tokenHash) {
        return PendingRegistration.builder()
                .username(username)
                .email(email)
                .passwordHash(PASSWORD_HASH)
                .zoneInfo(ZONE)
                .tokenHash(tokenHash)
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
    }

    // ========================================================================
    // Validación de comportamiento: constraints únicos
    // ========================================================================

    @Nested
    @DisplayName("Unique validation")
    class UniqueValidation {

        @Nested
        @DisplayName("uk_pending_email_lower")
        class EmailUnique {

            @Test
            @DisplayName("SHOULD fail with DataIntegrityViolation (uk_pending_email_lower) WHEN saving a second row with the same email in different case")
            void shouldFailWithDataIntegrityViolation_whenEmailBelongsToAnotherRow_ignoreCase() {
                // Arrange
                pendingRegistrationRepository.save(
                        pendingRow("user1", "User@Mail.com", "a".repeat(64))
                );
                PendingRegistration duplicateRow = pendingRow(
                        "user2",
                        "user@mail.com",
                        "b".repeat(64)
                );

                // Act & Assert
                assertThatThrownBy(() -> pendingRegistrationRepository.save(duplicateRow))
                        .isInstanceOf(DataIntegrityViolationException.class);
            }
        }

        @Nested
        @DisplayName("uk_pending_username_lower")
        class UsernameUnique {

            @Test
            @DisplayName("SHOULD fail with DataIntegrityViolation (uk_pending_username_lower) WHEN saving a second row with the same username in different case")
            void shouldFailWithDataIntegrityViolation_whenUsernameBelongsToAnotherRow_ignoreCase() {
                // Arrange
                pendingRegistrationRepository.save(
                        pendingRow("NewUser", "new@usermail.com", "a".repeat(64))
                );
                PendingRegistration duplicateRow = pendingRow(
                        "newuser",
                        "other@usermail.com",
                        "b".repeat(64)
                );

                // Act & Assert
                assertThatThrownBy(() -> pendingRegistrationRepository.save(duplicateRow))
                        .isInstanceOf(DataIntegrityViolationException.class);
            }
        }

        @Nested
        @DisplayName("uk_pending_token_hash")
        class TokenHashUnique {

            @Test
            @DisplayName("SHOULD fail with DataIntegrityViolation (uk_pending_token_hash) WHEN saving a row with a duplicate token_hash")
            void shouldFailWithDataIntegrityViolation_whenTokenHashIsDuplicated() {
                // Arrange
                pendingRegistrationRepository.save(
                        pendingRow("user1", "user1@mail.com", "a".repeat(64))
                );
                PendingRegistration duplicateRow = pendingRow(
                        "user2",
                        "user2@mail.com",
                        "a".repeat(64)
                );

                // Act & Assert
                assertThatThrownBy(() -> pendingRegistrationRepository.save(duplicateRow))
                        .isInstanceOf(DataIntegrityViolationException.class);
            }
        }
    }

    // ========================================================================
    // Repositorio
    // ========================================================================

    @Nested
    @DisplayName("Repository methods")
    class RepositoryMethods {

        // ------------------------------------------------------------
        // deleteByEmailIgnoreCase
        // ------------------------------------------------------------

        @Nested
        @DisplayName("deleteByEmailIgnoreCase")
        class DeleteByEmailIgnoreCase {

            @Test
            @DisplayName("SHOULD delete case-insensitively WHEN email case differs")
            void shouldDeleteCaseInsensitively_whenEmailCaseDiffers() {
                // Arrange
                pendingRegistrationRepository.save(
                        pendingRow("pendinguser", "User@Mail.com", "a".repeat(64))
                );

                // Act
                int deletedRows = pendingRegistrationRepository.deleteByEmailIgnoreCase("user@mail.com");

                // Assert
                assertThat(deletedRows).isEqualTo(1);
                assertThat(pendingRegistrationRepository.count()).isZero();
            }
        }

        // ------------------------------------------------------------
        // findByTokenHash
        // ------------------------------------------------------------

        @Nested
        @DisplayName("findByTokenHash")
        class FindByTokenHash {

            @Test
            @DisplayName("SHOULD return the exact row WHEN hash matches, and empty WHEN it does not")
            void shouldReturnExactRow_whenHashMatches_andEmpty_whenItDoesNot() {
                // Arrange
                PendingRegistration savedRow = pendingRegistrationRepository.save(
                        pendingRow("hashuser", "hash@mail.com", "c".repeat(64))
                );

                // Act
                PendingRegistration found = pendingRegistrationRepository.findByTokenHash("c".repeat(64))
                        .orElseThrow(() -> new AssertionError("PendingRegistration no encontrado"));

                // Assert
                assertThat(found.getId()).isEqualTo(savedRow.getId());
                assertThat(found.getUsername()).isEqualTo("hashuser");
                assertThat(found.getEmail()).isEqualTo("hash@mail.com");
                assertThat(found.getPasswordHash()).isEqualTo(PASSWORD_HASH);
                assertThat(found.getZoneInfo()).isEqualTo(ZONE);
                assertThat(found.getTokenHash()).isEqualTo("c".repeat(64));

                // Act & Assert — hash desconocido
                assertThat(pendingRegistrationRepository.findByTokenHash("d".repeat(64)))
                        .isEmpty();
            }
        }
    }
}
