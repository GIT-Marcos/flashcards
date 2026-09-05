package com.cards.api.integration.service;

import com.cards.api.dto.request.ReviewRequest;
import com.cards.api.entity.Card;
import com.cards.api.entity.Deck;
import com.cards.api.entity.User;
import com.cards.api.infraestructure.mother.CardMother;
import com.cards.api.infraestructure.mother.DeckMother;
import com.cards.api.infraestructure.mother.StudySessionMother;
import com.cards.api.infraestructure.mother.UserMother;
import com.cards.api.integration.TestcontainersConfig;
import com.cards.api.repo.*;
import com.cards.api.service.ReviewService;
import com.cards.api.service.SessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doThrow;

@SpringBootTest(properties = {
    "application.security.jwt.secret-key=integrationTestSecretKeyForHS256",
    "application.security.jwt.expiration=900000",
    "application.security.jwt.refresh-token.expiration=604800000",
    "application.notifications.send-at-hour=9",
    "application.notifications.threshold-hours=20",
    "application.notifications.cron=0 0 0 * * *",
    "application.notifications.app-url=http://localhost:5173",
    "application.notifications.from-address=notificaciones@flashcards.app",
    "application.notifications.from-name=Flashcards App",
    "application.notifications.unsubscribe-token-expiration=2592000000",
    "application.notifications.api-url=http://localhost:8080",
    "application.security.secure-cookie=false",
    "application.security.same-site=Strict",
    "maileroo.api-key=integration-test-api-key",
    "maileroo.webhook-secret=integration-test-webhook-secret",
    "application.security.verification-token-expiration=86400000",
    "application.security.reset-token-expiration=900000"
})
@Import(TestcontainersConfig.class)
@DisplayName("ReviewService transaction rollback")
class ReviewServiceTransactionIntegrationTest {

    @Autowired
    private ReviewService reviewService;

    @Autowired
    private CardRepository cardRepository;

    @Autowired
    private CardReviewLogRepository cardReviewLogRepository;

    @Autowired
    private DeckRepository deckRepository;

    @Autowired
    private StudySessionRepository studySessionRepository;

    @Autowired
    private UserRepository userRepository;

    @MockitoBean
    private SessionService sessionService;

    private User user;
    private Deck deck;
    private Card card;

    @BeforeEach
    void setUp() {
        user = userRepository.save(
            UserMother.createMinimal(
                UserMother.uniqueUsername("rollbacktest", System.nanoTime()),
                UserMother.uniqueEmail("rollbacktest", System.nanoTime())
            )
        );
        deck = deckRepository.save(
            DeckMother.createPending(user, DeckMother.uniqueDeckName("rollback-deck"))
        );
        card = cardRepository.save(
            CardMother.createDueCard(deck, "rollback-front", "rollback-back", Instant.parse("2026-05-12T09:00:00Z"))
        );
        studySessionRepository.save(
            StudySessionMother.createForUser(user)
        );
    }

    @Nested
    @DisplayName("when updateMetrics fails")
    class WhenUpdateMetricsFails {

        @Test
        @DisplayName("should roll back entire review when updateMetrics throws")
        void shouldRollbackWhenMetricsFails() {
            Instant originalNextReview = card.getNextReviewDate();

            doThrow(new RuntimeException("Metrics DB failure"))
                .when(sessionService).updateMetrics(anyLong(), anyInt(), any(Instant.class));

            ReviewRequest request = new ReviewRequest(4);
            assertThatThrownBy(() -> reviewService.review(card.getId(), user.getId(), request))
                .isInstanceOf(RuntimeException.class);

            Card reloadedCard = cardRepository.findById(card.getId()).orElseThrow();
            assertThat(reloadedCard.getRepetitionCount())
                .as("Card SM-2 state should NOT have changed")
                .isZero();
            assertThat(reloadedCard.getIntervalDays())
                .as("Card interval should NOT have changed")
                .isZero();
            assertThat(reloadedCard.getNextReviewDate())
                .as("Card nextReviewDate should NOT have changed")
                .isEqualTo(originalNextReview);

            boolean logExists = cardReviewLogRepository.findAll().stream()
                .anyMatch(log -> log.getCard() != null && log.getCard().getId().equals(card.getId()));
            assertThat(logExists)
                .as("CardReviewLog should NOT have been created")
                .isFalse();

            Deck reloadedDeck = deckRepository.findById(deck.getId()).orElseThrow();
            assertThat(reloadedDeck.getHasPendingCards())
                .as("Deck hasPendingCards should NOT have changed")
                .isTrue();
        }
    }
}
