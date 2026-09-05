package com.cards.api.integration.data;

import com.cards.api.config.AuditConfig;
import com.cards.api.entity.*;
import com.cards.api.infraestructure.config.JpaTestConfig;
import com.cards.api.infraestructure.mother.*;
import com.cards.api.integration.TestcontainersConfig;
import com.cards.api.repo.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import({TestcontainersConfig.class, AuditConfig.class, JpaTestConfig.class})
class CardReviewLogDataIntegrationTest {

    @Autowired
    private CardReviewLogRepository cardReviewLogRepository;
    @Autowired
    private CardRepository cardRepository;
    @Autowired
    private DeckRepository deckRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private StudySessionRepository studySessionRepository;
    @Autowired
    private TestEntityManager testEntityManager;

    private User testUser;
    private Deck testDeck;
    private Card testCard;
    private StudySession testSession;

    @BeforeEach
    void setUp() {
        testUser = userRepository.save(
            UserMother.createMinimal(
                UserMother.uniqueUsername("logtest", System.currentTimeMillis()),
                UserMother.uniqueEmail("logtest", System.currentTimeMillis())
            )
        );

        testDeck = deckRepository.save(
            DeckMother.createWithUser(testUser, DeckMother.uniqueDeckName("Log Test Deck"))
        );

        testCard = cardRepository.save(
            CardMother.createNew(testDeck, "Front Test", "Back Test")
        );

        testSession = studySessionRepository.save(
            StudySessionMother.createForUser(testUser)
        );
    }

    @Nested
    @DisplayName("Valid CardReviewLog persistence")
    class SuccessfulPersistence {

        @Test
        @DisplayName("SHOULD persist log with card-derived fields WHEN entity is valid")
        void shouldPersistLogWithCardDerivedFieldsWhenEntityIsValid() {
            // Arrange
            CardReviewLog log = ReviewLogMother.create(testUser, testCard, testSession, 4);

            // Act
            CardReviewLog savedLog = cardReviewLogRepository.save(log);
            CardReviewLog fetchedLog = cardReviewLogRepository.findById(savedLog.getId()).orElseThrow();

            // Assert
            assertThat(fetchedLog)
                .extracting("quality", "easinessFactor", "repetitionCount", "intervalDays")
                .containsExactly(4, testCard.getEasinessFactor(), testCard.getRepetitionCount(), testCard.getIntervalDays());

            assertThat(fetchedLog.getCard().getId()).isEqualTo(testCard.getId());
            assertThat(fetchedLog.getUser().getId()).isEqualTo(testUser.getId());
            assertThat(fetchedLog.getStudySession().getId()).isEqualTo(testSession.getId());
        }

        @Test
        @DisplayName("SHOULD persist log with null card reference WHEN card was deleted")
        void shouldPersistLogWithNullCardReference() {
            // Arrange
            CardReviewLog log = CardReviewLog.builder()
                .quality(5)
                .easinessFactor(2.5)
                .intervalDays(1)
                .repetitionCount(0)
                .nextReviewDate(testCard.getNextReviewDate())
                .user(testUser)
                .studySession(testSession)
                .build();

            // Act
            CardReviewLog savedLog = cardReviewLogRepository.save(log);

            // Assert
            assertThat(savedLog.getId()).isNotNull();
            assertThat(savedLog.getCard()).isNull();
            assertThat(savedLog.getUser().getId()).isEqualTo(testUser.getId());
        }
    }

    @Nested
    @DisplayName("Null relationship validation")
    class NullValidations {

        @Test
        @DisplayName("SHOULD fail with DataIntegrityViolation WHEN user relation is null")
        void shouldFailWithDataIntegrityViolationWhenUserRelationIsNull() {
            // Arrange
            CardReviewLog log = CardReviewLog.builder()
                .quality(5)
                .card(testCard)
                .studySession(testSession)
                .user(null)
                .build();

            // Act & Assert
            assertThatThrownBy(() -> cardReviewLogRepository.save(log))
                .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("SHOULD fail with DataIntegrityViolation WHEN session relation is null")
        void shouldFailWithDataIntegrityViolationWhenSessionRelationIsNull() {
            // Arrange
            CardReviewLog log = CardReviewLog.builder()
                .quality(5)
                .card(testCard)
                .user(testUser)
                .studySession(null)
                .build();

            // Act & Assert
            assertThatThrownBy(() -> cardReviewLogRepository.save(log))
                .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Nested
    @DisplayName("Cascade delete behavior")
    class CascadeDeleteValidations {

        @Test
        @DisplayName("SHOULD preserve log with null card WHEN its card is deleted")
        void shouldPreserveLogWhenCardIsDeleted() {
            // Given: Parent entities already persisted
            CardReviewLog log = ReviewLogMother.create(testUser, testCard, testSession, 5);

            testCard.getReviewLogs().add(log);
            testSession.getLogs().add(log);

            CardReviewLog savedLog = cardReviewLogRepository.save(log);

            testEntityManager.flush();
            testEntityManager.clear();

            Long logId = savedLog.getId();
            Long cardId = testCard.getId();

            // When: Delete the parent card
            Card managedCard = cardRepository.findById(cardId).orElseThrow();

            cardRepository.delete(managedCard);

            cardRepository.flush();

            // Then: Log must survive with card = null
            CardReviewLog preservedLog = cardReviewLogRepository.findById(logId).orElseThrow();

            assertThat(preservedLog)
                .as("CardReviewLog must be preserved when its Card is deleted")
                .isNotNull();
            assertThat(preservedLog.getCard())
                .as("Card reference must be null after deletion")
                .isNull();
            assertThat(preservedLog.getUser().getId())
                .as("User must remain intact")
                .isEqualTo(testUser.getId());
        }

        @Test
        @DisplayName("SHOULD delete log WHEN its user is deleted")
        void shouldDeleteLogWhenUserIsDeleted() {
            // Given: Isolated user for test
            User user = userRepository.save(
                UserMother.createMinimal(
                    UserMother.uniqueUsername("cascadeuser", System.currentTimeMillis()),
                    UserMother.uniqueEmail("cascadeuser", System.currentTimeMillis())
                )
            );

            // Deck and Card associated with user
            Deck deck = deckRepository.save(DeckMother.createWithUser(user, DeckMother.uniqueDeckName("CascadeDeck")));
            Card card = cardRepository.save(CardMother.createNew(deck, "Front", "Back"));

            // StudySession for user
            StudySession session = studySessionRepository.save(StudySessionMother.createForUser(user));

            // Create log without persisting yet
            CardReviewLog log = ReviewLogMother.create(user, card, session, 5);

            // Manual bidirectional sync
            card.getReviewLogs().add(log);
            session.getLogs().add(log);

            // Persist log (all parents already managed)
            CardReviewLog savedLog = cardReviewLogRepository.save(log);

            testEntityManager.flush();
            testEntityManager.clear();

            Long logId = savedLog.getId();
            Long userId = user.getId();

            // When: Delete user
            User managedUser = userRepository.findById(userId).orElseThrow();
            userRepository.delete(managedUser);

            userRepository.flush();

            // Then: Log must have been deleted
            assertThat(cardReviewLogRepository.findById(logId))
                .as("CardReviewLog must be deleted when its User is deleted")
                .isEmpty();
        }

        @Test
        @DisplayName("SHOULD delete log WHEN its session is deleted")
        void shouldDeleteLogWhenSessionIsDeleted() {
            // Given: Parent entities persisted
            CardReviewLog log = ReviewLogMother.create(testUser, testCard, testSession, 5);

            // Manual bidirectional sync
            testCard.getReviewLogs().add(log);
            testSession.getLogs().add(log);

            CardReviewLog savedLog = cardReviewLogRepository.save(log);

            testEntityManager.flush();
            testEntityManager.clear();

            Long logId = savedLog.getId();
            Long sessionId = testSession.getId();

            // When: Delete parent session
            StudySession managedSession = studySessionRepository.findById(sessionId).orElseThrow();
            studySessionRepository.delete(managedSession);

            // Flush processes orphanRemoval defined in StudySession.logs
            studySessionRepository.flush();

            // Then: Log must have been deleted
            assertThat(cardReviewLogRepository.findById(logId))
                .as("CardReviewLog must be deleted when its StudySession is deleted (orphanRemoval)")
                .isEmpty();
        }
    }

    @Nested
    @DisplayName("Repository Methods")
    class RepositoryMethods {

        @Test
        @DisplayName("should find latest log for user even across different sessions")
        void findLatestLog() {
            cardReviewLogRepository.saveAndFlush(CardReviewLog.builder()
                .quality(3)
                .card(testCard)
                .user(testUser)
                .studySession(testSession)
                .build()
            );

            StudySession secondSession = studySessionRepository.save(StudySessionMother.createForUser(testUser));
            CardReviewLog latest = cardReviewLogRepository.saveAndFlush(CardReviewLog.builder()
                .quality(5)
                .card(testCard)
                .user(testUser)
                .studySession(secondSession)
                .build()
            );

            // When
            Optional<CardReviewLog> result = cardReviewLogRepository
                .findFirstByUser_IdOrderByCreatedAtDesc(testUser.getId());

            // Then
            assertThat(result).isPresent();
            assertThat(result.get().getId()).isEqualTo(latest.getId());
            assertThat(result.get().getQuality()).isEqualTo(5);
        }
    }
}
