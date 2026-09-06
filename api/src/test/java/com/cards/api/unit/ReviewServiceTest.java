package com.cards.api.unit;

import com.cards.api.dto.request.ReviewRequest;
import com.cards.api.dto.response.CardResponse;
import com.cards.api.entity.*;
import com.cards.api.exception.ResourceNotFoundException;
import com.cards.api.exception.domain.InvalidReviewDateException;
import com.cards.api.mapper.CardMapper;
import com.cards.api.repo.CardRepository;
import com.cards.api.repo.CardReviewLogRepository;
import com.cards.api.service.ReviewService;
import com.cards.api.service.SessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReviewService")
class ReviewServiceTest {

    @Mock
    private SessionService sessionService;
    @Mock
    private CardReviewLogRepository cardReviewLogRepo;
    @Mock
    private CardRepository cardRepo;
    @Mock
    private CardMapper cardMapper;
    private Clock clock;

    @Captor
    ArgumentCaptor<CardReviewLog> logCaptor;

    private ReviewService reviewService;

    private static final Long USER_ID = 1L;
    private static final Long DECK_ID = 10L;
    private static final Long CARD_ID = 100L;
    private static final Long SESSION_ID = 50L;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(
            Instant.parse("2026-05-12T10:00:00Z"),
            ZoneId.of("America/Buenos_Aires")
        );

        reviewService = new ReviewService(sessionService, cardReviewLogRepo, cardRepo, cardMapper, clock);
    }

    // ======================== HELPERS ========================

    private User createUser() {
        User user = User.builder()
            .username("reviewer")
            .email("rev@email.com")
            .passwordHash("hash")
            .zoneInfo("America/Buenos_Aires")
            .addRole(User.UserRole.ROLE_USER)
            .build();
        user.setId(USER_ID);
        user.setSessionThreshold(30);
        user.setStartOfDay(6);
        return user;
    }

    private Deck createDeck() {
        Deck deck = Deck.builder()
            .name("Spanish")
            .user(createUser())
            .hasPendingCards(true)
            .build();
        deck.setId(DECK_ID);
        return deck;
    }

    private Card createDueCard() {
        Card card = Card.builder()
            .front("hola")
            .back("hello")
            .deck(createDeck())
            .build();
        card.setId(CARD_ID);
        card.setNextReviewDate(Instant.parse("2026-05-12T09:00:00Z"));
        return card;
    }

    private StudySession createSession() {
        StudySession session = StudySession.builder()
            .user(createUser())
            .build();
        session.setId(SESSION_ID);
        return session;
    }

    // ======================== REVIEW ========================

    @Nested
    @DisplayName("review")
    class Review {

        @Test
        @DisplayName("should review card successfully: update SM-2 state, save log, update metrics")
        void shouldReviewSuccessfully() {
            ReviewRequest request = new ReviewRequest(4); // "bien"
            Card card = createDueCard();
            StudySession session = createSession();
            CardResponse expected = new CardResponse(card.getId(), card.getFront(), card.getBack(), card.getNextReviewDate());

            when(cardRepo.findWithDeckAndUser(CARD_ID, USER_ID)).thenReturn(Optional.of(card));
            when(sessionService.getOrCreateActiveSession(USER_ID)).thenReturn(session);
            doAnswer(inv -> {
                session.updateMetrics(inv.getArgument(1), inv.getArgument(2));
                return null;
            }).when(sessionService).updateMetrics(anyLong(), anyInt(), any(Instant.class));
            when(cardRepo.existsByDeckIdAndNextReviewDateBefore(eq(DECK_ID), any())).thenReturn(true);
            when(cardReviewLogRepo.save(any(CardReviewLog.class))).thenAnswer(inv -> inv.getArgument(0));
            when(cardMapper.toResponse(any(Card.class))).thenReturn(expected);

            CardResponse response = reviewService.review(CARD_ID, USER_ID, request);

            assertThat(response).isNotNull();

            // Verify SM-2 was applied
            assertThat(card.getRepetitionCount()).isEqualTo(1);
            assertThat(card.getEasinessFactor()).isGreaterThan(0);

            // Verify review log was saved with correct quality
            verify(cardReviewLogRepo).save(logCaptor.capture());
            CardReviewLog log = logCaptor.getValue();
            assertThat(log.getQuality()).isEqualTo(4);

            // Verify session metrics updated
            assertThat(session.getCardsReviewed()).isEqualTo(1);
            assertThat(session.getEndTime()).isNotNull();
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when card not found for user")
        void shouldThrowWhenCardNotFound() {
            ReviewRequest request = new ReviewRequest(3);

            when(cardRepo.findWithDeckAndUser(CARD_ID, USER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> reviewService.review(CARD_ID, USER_ID, request))
                .isInstanceOf(ResourceNotFoundException.class);

            verifyNoInteractions(sessionService);
            verifyNoInteractions(cardReviewLogRepo);
        }

        @Test
        @DisplayName("should throw InvalidReviewDateException when card is not yet due")
        void shouldThrowWhenCardNotDue() {
            ReviewRequest request = new ReviewRequest(3);
            Card card = Card.builder()
                .front("hola")
                .back("hello")
                .deck(createDeck())
                .build();
            card.setId(CARD_ID);
            card.setNextReviewDate(Instant.parse("2026-05-12T11:00:00Z"));

            when(cardRepo.findWithDeckAndUser(CARD_ID, USER_ID)).thenReturn(Optional.of(card));

            assertThatThrownBy(() -> reviewService.review(CARD_ID, USER_ID, request))
                .isInstanceOf(InvalidReviewDateException.class);

            verifyNoInteractions(sessionService);
            verifyNoInteractions(cardReviewLogRepo);
        }

        @Test
        @DisplayName("should set deck.hasPendingCards to false when no more pending cards")
        void shouldSetHasPendingCardsFalse() {
            ReviewRequest request = new ReviewRequest(5);
            Card card = createDueCard();
            StudySession session = createSession();
            CardResponse expected = new CardResponse(card.getId(), card.getFront(), card.getBack(), card.getNextReviewDate());

            when(cardRepo.findWithDeckAndUser(CARD_ID, USER_ID)).thenReturn(Optional.of(card));
            when(sessionService.getOrCreateActiveSession(USER_ID)).thenReturn(session);
            when(cardRepo.existsByDeckIdAndNextReviewDateBefore(eq(DECK_ID), any())).thenReturn(false);
            when(cardReviewLogRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(cardMapper.toResponse(any(Card.class))).thenReturn(expected);

            reviewService.review(CARD_ID, USER_ID, request);

            verify(sessionService).updateMetrics(eq(SESSION_ID), eq(5), any(Instant.class));
            assertThat(card.getDeck().getHasPendingCards()).isFalse();
        }

        @Test
        @DisplayName("should keep deck.hasPendingCards true when more pending cards exist")
        void shouldKeepHasPendingCardsTrue() {
            ReviewRequest request = new ReviewRequest(3);
            Card card = createDueCard();
            StudySession session = createSession();
            CardResponse expected = new CardResponse(card.getId(), card.getFront(), card.getBack(), card.getNextReviewDate());

            when(cardRepo.findWithDeckAndUser(CARD_ID, USER_ID)).thenReturn(Optional.of(card));
            when(sessionService.getOrCreateActiveSession(USER_ID)).thenReturn(session);
            when(cardRepo.existsByDeckIdAndNextReviewDateBefore(eq(DECK_ID), any())).thenReturn(true);
            when(cardReviewLogRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(cardMapper.toResponse(any(Card.class))).thenReturn(expected);

            reviewService.review(CARD_ID, USER_ID, request);

            verify(sessionService).updateMetrics(eq(SESSION_ID), eq(3), any(Instant.class));
            assertThat(card.getDeck().getHasPendingCards()).isTrue();
        }

        @Test
        @DisplayName("should reset repetition count and interval on quality < 3 (failure)")
        void shouldResetProgressOnFailure() {
            ReviewRequest request = new ReviewRequest(1); // complete blackout
            Card card = createDueCard();
            card.setRepetitionCount(5);
            card.setIntervalDays(15);
            StudySession session = createSession();
            CardResponse expected = new CardResponse(card.getId(), card.getFront(), card.getBack(), card.getNextReviewDate());

            when(cardRepo.findWithDeckAndUser(CARD_ID, USER_ID)).thenReturn(Optional.of(card));
            when(sessionService.getOrCreateActiveSession(USER_ID)).thenReturn(session);
            when(cardRepo.existsByDeckIdAndNextReviewDateBefore(eq(DECK_ID), any())).thenReturn(true);
            when(cardReviewLogRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(cardMapper.toResponse(any(Card.class))).thenReturn(expected);

            reviewService.review(CARD_ID, USER_ID, request);

            verify(sessionService).updateMetrics(eq(SESSION_ID), eq(1), any(Instant.class));
            assertThat(card.getIntervalDays()).isEqualTo(1);
            assertThat(card.getRepetitionCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("should calculate accuracy rate correctly across multiple reviews")
        void shouldCalculateAccuracyRateCorrectly() {
            ReviewRequest successRequest = new ReviewRequest(3);
            Card card1 = createDueCard();
            StudySession session = createSession();
            CardResponse expected1 = new CardResponse(card1.getId(), card1.getFront(), card1.getBack(), card1.getNextReviewDate());

            when(cardRepo.findWithDeckAndUser(CARD_ID, USER_ID)).thenReturn(Optional.of(card1));
            when(sessionService.getOrCreateActiveSession(USER_ID)).thenReturn(session);
            doAnswer(inv -> {
                session.updateMetrics(inv.getArgument(1), inv.getArgument(2));
                return null;
            }).when(sessionService).updateMetrics(anyLong(), anyInt(), any(Instant.class));
            when(cardRepo.existsByDeckIdAndNextReviewDateBefore(eq(DECK_ID), any())).thenReturn(true);
            when(cardReviewLogRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(cardMapper.toResponse(any(Card.class))).thenReturn(expected1);

            reviewService.review(CARD_ID, USER_ID, successRequest);

            assertThat(session.getCardsReviewed()).isEqualTo(1);
            assertThat(session.getAccuracyRate()).isEqualTo(1.0);

            ReviewRequest failRequest = new ReviewRequest(2);
            Card card2 = createDueCard();
            card2.setId(101L);
            CardResponse expected2 = new CardResponse(card2.getId(), card2.getFront(), card2.getBack(), card2.getNextReviewDate());

            when(cardRepo.findWithDeckAndUser(101L, USER_ID)).thenReturn(Optional.of(card2));
            when(cardMapper.toResponse(any(Card.class))).thenReturn(expected2);

            reviewService.review(101L, USER_ID, failRequest);

            assertThat(session.getCardsReviewed()).isEqualTo(2);
            assertThat(session.getAccuracyRate()).isEqualTo(0.5);
        }
    }
}
