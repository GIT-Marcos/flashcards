package com.cards.api.integration.data;

import com.cards.api.config.AuditConfig;
import com.cards.api.dto.DataForNotificationDTO;
import com.cards.api.entity.Card;
import com.cards.api.entity.Deck;
import com.cards.api.entity.User;
import com.cards.api.infraestructure.config.JpaTestConfig;
import com.cards.api.infraestructure.mother.CardMother;
import com.cards.api.infraestructure.mother.DeckMother;
import com.cards.api.infraestructure.mother.UserMother;
import com.cards.api.integration.TestcontainersConfig;
import com.cards.api.repo.CardRepository;
import com.cards.api.repo.DeckRepository;
import com.cards.api.repo.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@Import({TestcontainersConfig.class, AuditConfig.class, JpaTestConfig.class})
class UserDataIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DeckRepository deckRepository;

    @Autowired
    private CardRepository cardRepository;

    @Autowired
    private TestEntityManager entityManager;

    @BeforeEach
    void setUp() {
        userRepository.deleteAllInBatch();
    }

    @Nested
    @DisplayName("Unique validation")
    class UniqueValidation {

        @Nested
        @DisplayName("uk_users_username_lower")
        class UsernameUnique {

            @Test
            @DisplayName("SHOULD fail with DataIntegrityViolation (uk_users_username_lower) WHEN username belongs to another user")
            void shouldFailWithDataIntegrityViolation_whenUsernameBelongsToAnotherUser() {
                // Arrange
                User existingUser = userRepository.save(
                    UserMother.createMinimal(
                        UserMother.uniqueUsername("johndoe", System.nanoTime()),
                        UserMother.uniqueEmail("johndoe", System.nanoTime())
                    )
                );
                User duplicateUser = UserMother.createMinimal(
                    existingUser.getUsername(),
                    UserMother.uniqueEmail("other", System.nanoTime())
                );

                // Act & Assert
                assertThatThrownBy(() -> userRepository.save(duplicateUser))
                    .isInstanceOf(DataIntegrityViolationException.class);
            }

            @Test
            @DisplayName("SHOULD fail with DataIntegrityViolation (uk_users_username_lower) WHEN username belongs to another user (ignore case)")
            void shouldFailWithDataIntegrityViolation_whenUsernameBelongsToAnotherUser_ignoreCase() {
                // Arrange
                User existingUser = userRepository.save(
                    UserMother.createMinimal(
                        "johndoe",
                        UserMother.uniqueEmail("johndoe", System.nanoTime())
                    )
                );
                User duplicateUser = UserMother.createMinimal(
                    "JOHNDOE",
                    UserMother.uniqueEmail("other", System.nanoTime())
                );

                // Act & Assert
                assertThatThrownBy(() -> userRepository.save(duplicateUser))
                    .isInstanceOf(DataIntegrityViolationException.class);
            }
        }

        @Nested
        @DisplayName("uk_users_email_lower")
        class EmailUnique {

            @Test
            @DisplayName("SHOULD fail with DataIntegrityViolation (uk_users_email_lower) WHEN email belongs to another user")
            void shouldFailWithDataIntegrityViolation_whenEmailBelongsToAnotherUser() {
                // Arrange
                User existingUser = userRepository.save(
                    UserMother.createMinimal(
                        UserMother.uniqueUsername("user1", System.nanoTime()),
                        "johndoe@example.com"
                    )
                );
                User duplicateUser = UserMother.createMinimal(
                    UserMother.uniqueUsername("user2", System.nanoTime()),
                    existingUser.getEmail()
                );

                // Act & Assert
                assertThatThrownBy(() -> userRepository.save(duplicateUser))
                    .isInstanceOf(DataIntegrityViolationException.class);
            }

            @Test
            @DisplayName("SHOULD fail with DataIntegrityViolation (uk_users_email_lower) WHEN email belongs to another user (ignore case)")
            void shouldFailWithDataIntegrityViolation_whenEmailBelongsToAnotherUser_ignoreCase() {
                // Arrange
                User existingUser = userRepository.save(
                    UserMother.createMinimal(
                        UserMother.uniqueUsername("user1", System.nanoTime()),
                        "johndoe@example.com"
                    )
                );
                User duplicateUser = UserMother.createMinimal(
                    UserMother.uniqueUsername("user2", System.nanoTime()),
                    "JOHNDOE@EXAMPLE.COM"
                );

                // Act & Assert
                assertThatThrownBy(() -> userRepository.save(duplicateUser))
                    .isInstanceOf(DataIntegrityViolationException.class);
            }
        }
    }

    // ========================================================================
    // Validación de comportamiento: CREACIÓN
    // ========================================================================

    @Nested
    @DisplayName("Creation behavior")
    class CreationBehavior {

        @Test
        @DisplayName("SHOULD apply default data WHEN user is created")
        void shouldApplyDefaultData_whenUserIsCreated() {
            // Arrange
            User user = UserMother.createMinimal(
                UserMother.uniqueUsername("newuser", System.nanoTime()),
                UserMother.uniqueEmail("newuser", System.nanoTime())
            );

            // Act
            User savedUser = userRepository.save(user);

            // Assert
            assertThat(savedUser.getSessionThreshold()).isEqualTo(30);
            assertThat(savedUser.getStartOfDay()).isEqualTo(6);
            assertThat(savedUser.isNotificationsEnabled()).isTrue();
        }
    }

// ========================================================================
    // Repositorio
    // ========================================================================

    @Nested
    @DisplayName("Repository methods")
    class RepositoryMethods {

        // ------------------------------------------------------------
        // existsByEmailIgnoreCase
        // ------------------------------------------------------------

        @Nested
        @DisplayName("existsByEmailIgnoreCase")
        class ExistsByEmailIgnoreCase {

            @Test
            @DisplayName("SHOULD return true WHEN another user with same email exists (ignore case)")
            void shouldReturnTrue_whenExistsUserWithSameEmail() {
                // Arrange
                userRepository.save(
                    UserMother.createMinimal(
                        UserMother.uniqueUsername("existing", System.nanoTime()),
                        "ana@example.com"
                    )
                );

                // Act
                boolean exists = userRepository.existsByEmailIgnoreCase("ANA@EXAMPLE.COM");

                // Assert
                assertThat(exists).isTrue();
            }
        }

        // ------------------------------------------------------------
        // existsByUsernameIgnoreCase
        // ------------------------------------------------------------

        @Nested
        @DisplayName("existsByUsernameIgnoreCase")
        class ExistsByUsernameIgnoreCase {

            @Test
            @DisplayName("SHOULD return true WHEN another user with same username exists (ignore case)")
            void shouldReturnTrue_whenExistsUserWithSameUsername() {
                // Arrange
                userRepository.save(
                    UserMother.createMinimal(
                        "carlos_dev",
                        UserMother.uniqueEmail("carlos", System.nanoTime())
                    )
                );

                // Act
                boolean exists = userRepository.existsByUsernameIgnoreCase("CARLOS_DEV");

                // Assert
                assertThat(exists).isTrue();
            }
        }

        // ------------------------------------------------------------
        // findByUsernameIgnoreCase
        // ------------------------------------------------------------

        @Nested
        @DisplayName("findByUsernameIgnoreCase")
        class FindByUsernameIgnoreCase {

            @Test
            @DisplayName("SHOULD find user WHEN searched by username (ignore case)")
            void shouldFindUser_whenSearchByUsernameIgnoreCase() {
                // Arrange
                User savedUser = userRepository.save(
                    UserMother.createMinimal(
                        "maria_garcia",
                        UserMother.uniqueEmail("maria", System.nanoTime())
                    )
                );

                // Act
                User found = userRepository.findByUsernameIgnoreCase("MARIA_GARCIA")
                    .orElseThrow(() -> new AssertionError("User no encontrado"));

                // Assert
                assertThat(found.getId()).isEqualTo(savedUser.getId());
                assertThat(found.getUsername()).isEqualTo("maria_garcia");
                assertThat(found.getEmail()).isEqualTo(savedUser.getEmail());
            }
        }

        // ------------------------------------------------------------
        // findDistinctActiveZoneInfos
        // ------------------------------------------------------------

        @Nested
        @DisplayName("findDistinctActiveZoneInfos")
        class FindDistinctActiveZoneInfos {

            @Test
            @DisplayName("SHOULD return distinct zones WHEN users exist")
            void shouldReturnDistinctZones_whenUsersExist() {
                // Arrange
                // Dos users comparten la zona default "Europe/Madrid" desde UserMother
                userRepository.save(
                    UserMother.createMinimal(
                        UserMother.uniqueUsername("madrid1", System.nanoTime()),
                        UserMother.uniqueEmail("madrid1", System.nanoTime())
                    )
                );
                userRepository.save(
                    UserMother.createMinimal(
                        UserMother.uniqueUsername("madrid2", System.nanoTime()),
                        UserMother.uniqueEmail("madrid2", System.nanoTime())
                    )
                );

                // Un user con zona diferente
                User baUser = UserMother.createMinimal(
                    UserMother.uniqueUsername("baires", System.nanoTime()),
                    UserMother.uniqueEmail("baires", System.nanoTime())
                );
                baUser.setZoneInfo("America/Buenos_Aires");
                userRepository.save(baUser);

                // Act
                List<String> zones = userRepository.findDistinctActiveZoneInfos();

                // Assert
                assertThat(zones)
                    .containsExactlyInAnyOrder("Europe/Madrid", "America/Buenos_Aires")
                    .hasSize(2);
            }
        }

        // ------------------------------------------------------------
        // findUsersToNotify
        // ------------------------------------------------------------

        @Nested
        @DisplayName("findUsersToNotify")
        class FindUsersToNotify {

            @Test
            @DisplayName("SHOULD return DataForNotificationDTO correctly WHEN searched")
            void shouldReturnDataForNotificationDTO_whenSearched() {
                // Arrange
                Instant now = Instant.now();
                Instant threshold = now.minus(Duration.ofHours(24));
                String zone = "Europe/Madrid";

                // User que cumple todos los criterios: zona correcta, nunca notificado, con card vencida
                User eligibleUser = userRepository.save(
                    UserMother.createMinimal(
                        UserMother.uniqueUsername("eligible", System.nanoTime()),
                        UserMother.uniqueEmail("eligible", System.nanoTime())
                    )
                );
                // Asegurar zona y que lastNotificationSent sea null (ya lo es por defecto en createMinimal)
                eligibleUser.setZoneInfo(zone);
                eligibleUser.setNotificationsEnabled(true);
                userRepository.flush();

                Deck deck = deckRepository.save(
                    DeckMother.createWithUser(eligibleUser, DeckMother.uniqueDeckName("Pending Deck"))
                );

                Card dueCard = CardMother.createDueCard(deck, "¿Capital de Francia?", "París", now);
                cardRepository.save(dueCard);

                // User con cards PERO en otra zona → no debe aparecer
                User otherZoneUser = userRepository.save(
                    UserMother.createMinimal(
                        UserMother.uniqueUsername("otherzone", System.nanoTime()),
                        UserMother.uniqueEmail("otherzone", System.nanoTime())
                    )
                );
                otherZoneUser.setZoneInfo("America/Buenos_Aires");
                userRepository.flush();

                Deck otherDeck = deckRepository.save(
                    DeckMother.createWithUser(otherZoneUser, DeckMother.uniqueDeckName("Other Deck"))
                );
                cardRepository.save(CardMother.createDueCard(otherDeck, "¿Capital de Italia?", "Roma", now));

                // Act
                List<DataForNotificationDTO> result = userRepository.findUsersToNotify(now, zone, threshold);

                // Assert
                assertThat(result).hasSize(1);

                DataForNotificationDTO dto = result.getFirst();
                assertThat(dto.id()).isEqualTo(eligibleUser.getId());
                assertThat(dto.username()).isEqualTo(eligibleUser.getUsername());
                assertThat(dto.email()).isEqualTo(eligibleUser.getEmail());
                assertThat(dto.zoneInfo()).isEqualTo(zone);
            }

            @Test
            @DisplayName("SHOULD exclude users WHEN notificationsEnabled is false")
            void shouldExcludeUserWhenNotificationsDisabled() {
                // Arrange
                Instant now = Instant.now();
                Instant threshold = now.minus(Duration.ofHours(24));
                String zone = "Europe/Madrid";

                User disabledUser = userRepository.save(
                    UserMother.createMinimal(
                        UserMother.uniqueUsername("disabled", System.nanoTime()),
                        UserMother.uniqueEmail("disabled", System.nanoTime())
                    )
                );
                disabledUser.setZoneInfo(zone);
                disabledUser.setNotificationsEnabled(false);
                userRepository.flush();

                Deck deck = deckRepository.save(
                    DeckMother.createWithUser(disabledUser, DeckMother.uniqueDeckName("Disabled Deck"))
                );
                cardRepository.save(CardMother.createDueCard(deck, "Q", "A", now));

                // Act
                List<DataForNotificationDTO> result = userRepository.findUsersToNotify(now, zone, threshold);

                // Assert
                assertThat(result).isEmpty();
            }
        }

        // ------------------------------------------------------------
        // countActiveUsersSince
        // ------------------------------------------------------------

        @Nested
        @DisplayName("countActiveUsersSince")
        class CountActiveUsersSince {

            @Test
            @DisplayName("SHOULD count users WHEN lastLogin is within the time window")
            void shouldCountUsersWithinWindow() {
                // Arrange
                Instant now = Instant.now();
                Instant windowStart = now.minus(Duration.ofDays(30));

                User recentUser1 = userRepository.save(
                    UserMother.createMinimal(
                        UserMother.uniqueUsername("recent1", System.nanoTime()),
                        UserMother.uniqueEmail("recent1", System.nanoTime())
                    )
                );
                recentUser1.setLastLogin(now.minus(Duration.ofDays(5)));

                User recentUser2 = userRepository.save(
                    UserMother.createMinimal(
                        UserMother.uniqueUsername("recent2", System.nanoTime()),
                        UserMother.uniqueEmail("recent2", System.nanoTime())
                    )
                );
                recentUser2.setLastLogin(now.minus(Duration.ofDays(15)));

                userRepository.flush();
                entityManager.clear();

                // Act
                long count = userRepository.countActiveUsersSince(windowStart);

                // Assert
                assertThat(count).isEqualTo(2);
            }

            @Test
            @DisplayName("SHOULD NOT count users WHEN lastLogin is outside the time window")
            void shouldNotCountUsersOutsideWindow() {
                // Arrange
                Instant now = Instant.now();
                Instant windowStart = now.minus(Duration.ofDays(30));

                User oldUser = userRepository.save(
                    UserMother.createMinimal(
                        UserMother.uniqueUsername("olduser", System.nanoTime()),
                        UserMother.uniqueEmail("olduser", System.nanoTime())
                    )
                );
                oldUser.setLastLogin(now.minus(Duration.ofDays(60)));

                User noLoginUser = userRepository.save(
                    UserMother.createMinimal(
                        UserMother.uniqueUsername("nologin", System.nanoTime()),
                        UserMother.uniqueEmail("nologin", System.nanoTime())
                    )
                );
                // lastLogin is null by default

                userRepository.flush();
                entityManager.clear();

                // Act
                long count = userRepository.countActiveUsersSince(windowStart);

                // Assert
                assertThat(count).isZero();
            }

            @Test
            @DisplayName("SHOULD count only active users WHEN mixed login dates exist")
            void shouldCountOnlyActiveUsersWhenMixed() {
                // Arrange
                Instant now = Instant.now();
                Instant windowStart = now.minus(Duration.ofDays(30));

                User activeUser = userRepository.save(
                    UserMother.createMinimal(
                        UserMother.uniqueUsername("active", System.nanoTime()),
                        UserMother.uniqueEmail("active", System.nanoTime())
                    )
                );
                activeUser.setLastLogin(now.minus(Duration.ofDays(1)));

                User inactiveUser = userRepository.save(
                    UserMother.createMinimal(
                        UserMother.uniqueUsername("inactive", System.nanoTime()),
                        UserMother.uniqueEmail("inactive", System.nanoTime())
                    )
                );
                inactiveUser.setLastLogin(now.minus(Duration.ofDays(90)));

                userRepository.flush();
                entityManager.clear();

                // Act
                long count = userRepository.countActiveUsersSince(windowStart);

                // Assert
                assertThat(count).isEqualTo(1);
            }
        }

        // ------------------------------------------------------------
        // updateLastLogin
        // ------------------------------------------------------------

        @Nested
        @DisplayName("updateLastLogin")
        class UpdateLastLogin {

            @Test
            @DisplayName("SHOULD update lastLogin correctly WHEN executed")
            void shouldUpdateLastLoginCorrectly() {
                // Arrange
                User savedUser = userRepository.save(
                    UserMother.createMinimal(
                        UserMother.uniqueUsername("loginuser", System.nanoTime()),
                        UserMother.uniqueEmail("loginuser", System.nanoTime())
                    )
                );
                assertThat(savedUser.getLastLogin()).isNull();

                Instant loginTime = Instant.now().plusSeconds(60);

                // Act
                int updatedRows = userRepository.updateLastLogin(savedUser.getId(), loginTime);

                // Assert — limpiar el persistence context para evitar datos stale
                entityManager.clear();
                User refreshedUser = userRepository.findById(savedUser.getId())
                    .orElseThrow(() -> new AssertionError("User no encontrado tras updateLastLogin"));

                assertThat(updatedRows).isEqualTo(1);
                // Después (tolera la pérdida de precisión de PG)
                assertThat(refreshedUser.getLastLogin()).isCloseTo(loginTime, within(1, ChronoUnit.MILLIS));
            }
        }
    }
}
