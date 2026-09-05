package com.cards.api.smoke;

import com.cards.api.config.AuditConfig;
import com.cards.api.config.properties.EncryptionProperties;
import com.cards.api.entity.*;
import com.cards.api.infraestructure.mother.*;
import com.cards.api.integration.TestcontainersConfig;
import com.cards.api.repo.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Smoke test that verifies database connectivity via Testcontainers PostgreSQL.
 *
 * <p>This test validates that:
 * <ul>
 *   <li>Testcontainers starts a real PostgreSQL 15 instance</li>
 *   <li>Flyway migrations run successfully</li>
 *   <li>Basic CRUD operations work on all entities</li>
 *   <li>Database constraints (unique, not-null) are enforced</li>
 *   <li>Entity relationships work correctly (OneToMany, ManyToOne)</li>
 * </ul>
 *
 * <p>Uses {@code @DataJpaTest} for JPA slice testing with real PostgreSQL
 * via {@link TestcontainersConfig}.</p>
 */
@DataJpaTest
@Import({TestcontainersConfig.class, AuditConfig.class, DatabaseConnectivitySmokeTest.EncryptionConfig.class})
@DisplayName("Smoke: Database Connectivity")
class DatabaseConnectivitySmokeTest {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private DeckRepository deckRepository;
    @Autowired
    private CardRepository cardRepository;
    @Autowired
    private CardReviewLogRepository cardReviewLogRepository;
    @Autowired
    private StudySessionRepository studySessionRepository;
    @Autowired
    private TestEntityManager entityManager;

    @BeforeEach
    void cleanDatabase() {
        cardReviewLogRepository.deleteAllInBatch();
        studySessionRepository.deleteAllInBatch();
        cardRepository.deleteAllInBatch();
        deckRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    // ================================================================== //
    //  User CRUD
    // ================================================================== //

    @Nested
    @DisplayName("User persistence")
    class UserPersistence {

        @Test
        @DisplayName("should persist and retrieve a user")
        void shouldPersistAndRetrieveUser() {
            // Arrange
            User user = UserMother.createMinimal(
                UserMother.uniqueUsername("smoke", System.nanoTime()),
                UserMother.uniqueEmail("smoke", System.nanoTime())
            );

            // Act
            User saved = userRepository.save(user);
            entityManager.flush();
            entityManager.clear();

            // Assert
            Optional<User> found = userRepository.findById(saved.getId());
            assertThat(found).isPresent();
            assertThat(found.get().getUsername()).isEqualTo(user.getUsername());
            assertThat(found.get().getEmail()).isEqualTo(user.getEmail().toLowerCase());
            assertThat(found.get().getPasswordHash()).isEqualTo(user.getPasswordHash());
            assertThat(found.get().getCreatedAt()).isNotNull();
            assertThat(found.get().getUpdatedAt()).isNotNull();
        }

        @Test
        @DisplayName("should enforce unique username constraint")
        void shouldEnforceUniqueUsernameConstraint() {
            // Arrange
            String username = UserMother.uniqueUsername("unique", System.nanoTime());
            User user1 = UserMother.createMinimal(username, "user1@test.com");
            userRepository.saveAndFlush(user1);

            // Act & Assert
            User user2 = UserMother.createMinimal(username, "user2@test.com");
            assertThatThrownBy(() -> userRepository.saveAndFlush(user2))
                .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("should enforce unique email constraint")
        void shouldEnforceUniqueEmailConstraint() {
            // Arrange
            String email = UserMother.uniqueEmail("unique", System.nanoTime());
            User user1 = UserMother.createMinimal("user1", email);
            userRepository.saveAndFlush(user1);

            // Act & Assert
            User user2 = UserMother.createMinimal("user2", email);
            assertThatThrownBy(() -> userRepository.saveAndFlush(user2))
                .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("should delete a user")
        void shouldDeleteUser() {
            // Arrange
            User user = userRepository.save(
                UserMother.createMinimal(
                    UserMother.uniqueUsername("delete", System.nanoTime()),
                    UserMother.uniqueEmail("delete", System.nanoTime())
                )
            );
            entityManager.flush();

            // Act
            userRepository.delete(user);
            userRepository.flush();

            // Assert
            assertThat(userRepository.findById(user.getId())).isEmpty();
        }
    }

    // ================================================================== //
    //  Deck CRUD
    // ================================================================== //

    @Nested
    @DisplayName("Deck persistence")
    class DeckPersistence {

        private User testUser;

        @BeforeEach
        void createTestUser() {
            testUser = userRepository.save(
                UserMother.createMinimal(
                    UserMother.uniqueUsername("deckowner", System.nanoTime()),
                    UserMother.uniqueEmail("deckowner", System.nanoTime())
                )
            );
        }

        @Test
        @DisplayName("should persist a deck with user relationship")
        void shouldPersistDeckWithUser() {
            // Arrange
            Deck deck = DeckMother.createWithUser(testUser, "Spanish Vocabulary");

            // Act
            Deck saved = deckRepository.save(deck);
            entityManager.flush();
            entityManager.clear();

            // Assert
            Optional<Deck> found = deckRepository.findById(saved.getId());
            assertThat(found).isPresent();
            assertThat(found.get().getName()).isEqualTo("Spanish Vocabulary");
            assertThat(found.get().getUser().getId()).isEqualTo(testUser.getId());
            assertThat(found.get().getCreatedAt()).isNotNull();
        }

        @Test
        @DisplayName("should enforce deck belongs to a user (NOT NULL)")
        void shouldEnforceDeckBelongsToUser() {
            // Arrange
            Deck orphanDeck = Deck.builder()
                .name("Orphan Deck")
                .build();
            // user is null

            // Act & Assert
            assertThatThrownBy(() -> deckRepository.saveAndFlush(orphanDeck))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("user_id");
        }
    }

    // ================================================================== //
    //  Card CRUD
    // ================================================================== //

    @Nested
    @DisplayName("Card persistence")
    class CardPersistence {

        private User testUser;
        private Deck testDeck;

        @BeforeEach
        void createTestData() {
            testUser = userRepository.save(
                UserMother.createMinimal(
                    UserMother.uniqueUsername("cardowner", System.nanoTime()),
                    UserMother.uniqueEmail("cardowner", System.nanoTime())
                )
            );
            testDeck = deckRepository.save(
                DeckMother.createWithUser(testUser, DeckMother.uniqueDeckName("Cards Smoke"))
            );
        }

        @Test
        @DisplayName("should persist a card with deck relationship")
        void shouldPersistCardWithDeck() {
            // Arrange
            Card card = CardMother.createNew(testDeck, "What is Spring Boot?", "A Java framework");

            // Act
            Card saved = cardRepository.save(card);
            entityManager.flush();
            entityManager.clear();

            // Assert
            Optional<Card> found = cardRepository.findById(saved.getId());
            assertThat(found).isPresent();
            assertThat(found.get().getFront()).isEqualTo("What is Spring Boot?");
            assertThat(found.get().getBack()).isEqualTo("A Java framework");
            assertThat(found.get().getDeck().getId()).isEqualTo(testDeck.getId());
            assertThat(found.get().getEasinessFactor()).isEqualTo(2.5);
            assertThat(found.get().getIntervalDays()).isEqualTo(0);
            assertThat(found.get().getRepetitionCount()).isEqualTo(0);
            assertThat(found.get().getNextReviewDate()).isNotNull();
        }

        @Test
        @DisplayName("should enforce unique front per deck")
        void shouldEnforceUniqueFrontPerDeck() {
            // Arrange
            cardRepository.saveAndFlush(CardMother.createNew(testDeck, "Duplicate?", "First"));

            // Act & Assert
            Card duplicate = CardMother.createNew(testDeck, "Duplicate?", "Second");
            assertThatThrownBy(() -> cardRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("should cascade delete cards when deck is deleted")
        void shouldCascadeDeleteCardsWhenDeckIsDeleted() {
            // Arrange
            cardRepository.saveAllAndFlush(java.util.List.of(
                CardMother.createNew(testDeck, "Q1", "A1"),
                CardMother.createNew(testDeck, "Q2", "A2")
            ));

            Long deckId = testDeck.getId();
            assertThat(cardRepository.count()).isEqualTo(2);

            // Clear persistence context so JPA doesn't try to disassociate cards first
            entityManager.flush();
            entityManager.clear();

            // Act: reload and delete the managed deck
            Deck managedDeck = deckRepository.findById(deckId).orElseThrow();
            deckRepository.delete(managedDeck);
            deckRepository.flush();

            // Assert
            assertThat(cardRepository.count()).isZero();
        }
    }

    // ================================================================== //
    //  StudySession CRUD
    // ================================================================== //

    @Nested
    @DisplayName("StudySession persistence")
    class StudySessionPersistence {

        private User testUser;

        @BeforeEach
        void createTestUser() {
            testUser = userRepository.save(
                UserMother.createMinimal(
                    UserMother.uniqueUsername("sessionuser", System.nanoTime()),
                    UserMother.uniqueEmail("sessionuser", System.nanoTime())
                )
            );
        }

        @Test
        @DisplayName("should persist a study session with user relationship")
        void shouldPersistStudySession() {
            // Arrange
            StudySession session = StudySessionMother.createForUser(testUser);

            // Act
            StudySession saved = studySessionRepository.save(session);
            entityManager.flush();
            entityManager.clear();

            // Assert
            Optional<StudySession> found = studySessionRepository.findById(saved.getId());
            assertThat(found).isPresent();
            assertThat(found.get().getUser().getId()).isEqualTo(testUser.getId());
            assertThat(found.get().getStartTime()).isNotNull();
            assertThat(found.get().getCardsReviewed()).isEqualTo(0);
        }
    }

    // ================================================================== //
    //  CardReviewLog CRUD
    // ================================================================== //

    @Nested
    @DisplayName("CardReviewLog persistence")
    class CardReviewLogPersistence {

        private User testUser;
        private Deck testDeck;
        private Card testCard;
        private StudySession testSession;

        @BeforeEach
        void createTestData() {
            testUser = userRepository.save(
                UserMother.createMinimal(
                    UserMother.uniqueUsername("loguser", System.nanoTime()),
                    UserMother.uniqueEmail("loguser", System.nanoTime())
                )
            );
            testDeck = deckRepository.save(
                DeckMother.createWithUser(testUser, DeckMother.uniqueDeckName("Log Smoke Deck"))
            );
            testCard = cardRepository.save(
                CardMother.createNew(testDeck, "Review Q", "Review A")
            );
            testSession = studySessionRepository.save(
                StudySessionMother.createForUser(testUser)
            );
        }

        @Test
        @DisplayName("should persist a review log with all relationships")
        void shouldPersistReviewLog() {
            // Arrange
            CardReviewLog log = ReviewLogMother.create(testUser, testCard, testSession, 4);

            // Act
            CardReviewLog saved = cardReviewLogRepository.save(log);
            entityManager.flush();
            entityManager.clear();

            // Assert
            Optional<CardReviewLog> found = cardReviewLogRepository.findById(saved.getId());
            assertThat(found).isPresent();
            assertThat(found.get().getQuality()).isEqualTo(4);
            assertThat(found.get().getCard().getId()).isEqualTo(testCard.getId());
            assertThat(found.get().getUser().getId()).isEqualTo(testUser.getId());
            assertThat(found.get().getStudySession().getId()).isEqualTo(testSession.getId());
            assertThat(found.get().getCreatedAt()).isNotNull();
        }
    }

    // ================================================================== //
    //  Flyway migrations
    // ================================================================== //

    @Nested
    @DisplayName("Flyway schema validation")
    class FlywaySchemaValidation {

        @Test
        @DisplayName("database schema should be compatible with entities after Flyway migrations")
        void databaseSchemaShouldBeCompatibleWithEntities() {
            // If Flyway ran successfully, all entity mappings work.
            // We verify this by performing a full entity lifecycle:
            // create → read → delete for each entity type.

            // Create user
            User user = userRepository.save(
                UserMother.createMinimal(
                    UserMother.uniqueUsername("flyway", System.nanoTime()),
                    UserMother.uniqueEmail("flyway", System.nanoTime())
                )
            );

            // Create deck (FK to user)
            Deck deck = deckRepository.save(DeckMother.createWithUser(user, "Flyway Deck"));

            // Create card (FK to deck)
            Card card = cardRepository.save(CardMother.createNew(deck, "F", "B"));

            // Create session (FK to user)
            StudySession session = studySessionRepository.save(StudySessionMother.createForUser(user));

            // Create review log (FKs to user, card, session)
            CardReviewLog log = cardReviewLogRepository.save(
                ReviewLogMother.create(user, card, session, 5)
            );

            // Flush all to database
            entityManager.flush();
            entityManager.clear();

            // Verify everything persisted
            assertThat(userRepository.findById(user.getId())).isPresent();
            assertThat(deckRepository.findById(deck.getId())).isPresent();
            assertThat(cardRepository.findById(card.getId())).isPresent();
            assertThat(studySessionRepository.findById(session.getId())).isPresent();
            assertThat(cardReviewLogRepository.findById(log.getId())).isPresent();

            // Verify counts
            assertThat(userRepository.count()).isEqualTo(1);
            assertThat(deckRepository.count()).isEqualTo(1);
            assertThat(cardRepository.count()).isEqualTo(1);
            assertThat(studySessionRepository.count()).isEqualTo(1);
            assertThat(cardReviewLogRepository.count()).isEqualTo(1);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableConfigurationProperties(EncryptionProperties.class)
    static class EncryptionConfig {
    }
}
