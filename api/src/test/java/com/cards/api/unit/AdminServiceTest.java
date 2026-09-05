package com.cards.api.unit;

import com.cards.api.dto.request.CursorPaginationRequest;
import com.cards.api.dto.response.CardResponse;
import com.cards.api.dto.response.DeckResponse;
import com.cards.api.dto.response.UserResponse;
import com.cards.api.entity.Card;
import com.cards.api.entity.Deck;
import com.cards.api.entity.User;
import com.cards.api.exception.ResourceNotFoundException;
import com.cards.api.mapper.CardMapper;
import com.cards.api.mapper.DeckMapper;
import com.cards.api.mapper.UserMapper;
import com.cards.api.repo.CardRepository;
import com.cards.api.repo.DeckRepository;
import com.cards.api.repo.UserRepository;
import com.cards.api.service.AdminService;
import com.cards.api.service.notification.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.ScrollPosition;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Window;
import org.springframework.data.jpa.domain.Specification;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminService")
class AdminServiceTest {

    @Mock
    private DeckRepository deckRepo;
    @Mock
    private UserRepository userRepo;
    @Mock
    private CardRepository cardRepo;
    @Mock
    private UserMapper userMapper;
    @Mock
    private DeckMapper deckMapper;
    @Mock
    private CardMapper cardMapper;
    @Mock
    private EmailService emailService;
    @Mock
    private Clock clock;

    private AdminService adminService;

    private static final Long USER_ID = 1L;
    private static final Long DECK_ID = 10L;
    private static final Long CARD_ID = 100L;
    private static final Instant NOW = Instant.parse("2026-06-01T12:00:00Z");

    @BeforeEach
    void setUp() {
        adminService = new AdminService(deckRepo, userRepo, cardRepo, emailService, userMapper, deckMapper, cardMapper, clock);
    }

    // ======================== HELPERS ========================

    private User createUser(Long id, String username) {
        User user = User.builder()
            .username(username)
            .email(username + "@email.com")
            .passwordHash("hash")
            .zoneInfo("UTC")
            .addRole(User.UserRole.ROLE_USER)
            .build();
        user.setId(id);
        return user;
    }

    private Deck createDeck(Long id, String name, User owner) {
        Deck deck = Deck.builder()
            .name(name)
            .user(owner)
            .build();
        deck.setId(id);
        deck.setCreatedAt(Instant.now());
        deck.setUpdatedAt(Instant.now());
        return deck;
    }

    private Card createCard(Long id, String front, Deck deck) {
        Card card = Card.builder()
            .front(front)
            .back("back")
            .deck(deck)
            .build();
        card.setId(id);
        card.setNextReviewDate(Instant.now());
        return card;
    }

    // ======================== READ ========================

    @Nested
    @DisplayName("getAllUsers")
    class GetAllUsers {

        private final CursorPaginationRequest request =
            CursorPaginationRequest.forUsers(null, null, 15, Sort.Direction.DESC);

        @Test
        @DisplayName("should return window of all users with correct fields")
        void shouldReturnAllUsers() {
            User user1 = createUser(1L, "alice");
            User user2 = createUser(2L, "bob");
            user1.setEmail("alice@email.com");
            user2.setEmail("bob@email.com");
            user1.getRoles().add(User.UserRole.ROLE_USER);
            user2.getRoles().add(User.UserRole.ROLE_ADMIN);
            UserResponse resp1 = new UserResponse(user1.getId(), user1.getUsername(), user1.getEmail(), user1.getZoneInfo(), user1.getCreatedAt(), user1.getLastLogin(), user1.getLastNotificationSent(), user1.getSessionThreshold(), user1.getStartOfDay(), user1.isNotificationsEnabled(), user1.getRoles().stream().map(Enum::toString).collect(Collectors.toSet()));
            UserResponse resp2 = new UserResponse(user2.getId(), user2.getUsername(), user2.getEmail(), user2.getZoneInfo(), user2.getCreatedAt(), user2.getLastLogin(), user2.getLastNotificationSent(), user2.getSessionThreshold(), user2.getStartOfDay(), user2.isNotificationsEnabled(), user2.getRoles().stream().map(Enum::toString).collect(Collectors.toSet()));

            Window<User> userWindow = Window.from(
                List.of(user1, user2),
                i -> ScrollPosition.keyset(),
                false
            );

            when(userRepo.findBy(
                ArgumentMatchers.<Specification<User>>any(),
                any()
            )).thenReturn(userWindow);
            when(userMapper.toResponse(user1)).thenReturn(resp1);
            when(userMapper.toResponse(user2)).thenReturn(resp2);

            Window<UserResponse> result = adminService.getAllUsers(request);

            assertThat(result.getContent()).hasSize(2);

            UserResponse first = result.getContent().getFirst();
            assertThat(first.username()).isEqualTo("alice");
            assertThat(first.email()).isEqualTo("alice@email.com");
            assertThat(first.roles()).contains(User.UserRole.ROLE_USER.toString());

            UserResponse second = result.getContent().get(1);
            assertThat(second.username()).isEqualTo("bob");
            assertThat(second.email()).isEqualTo("bob@email.com");
            assertThat(second.roles()).contains(User.UserRole.ROLE_ADMIN.toString());
        }

        @Test
        @DisplayName("should return empty window when no users exist")
        void shouldReturnEmptyList() {
            Window<User> empty = Window.from(
                List.of(),
                i -> ScrollPosition.keyset(),
                false
            );

            when(userRepo.findBy(
                ArgumentMatchers.<Specification<User>>any(),
                any()
            )).thenReturn(empty);

            Window<UserResponse> result = adminService.getAllUsers(request);

            assertThat(result.getContent()).isEmpty();
        }
    }

    @Nested
    @DisplayName("getUserDecks")
    class GetUserDecks {

        private final CursorPaginationRequest request =
            CursorPaginationRequest.forDecks(null, null, 15, Sort.Direction.DESC);

        @Test
        @DisplayName("should return decks for given user with correct fields")
        void shouldReturnUserDecks() {
            User owner = createUser(USER_ID, "alice");
            Deck deck = createDeck(DECK_ID, "Spanish", owner);
            deck.setHasPendingCards(true);
            DeckResponse resp = new DeckResponse(deck.getId(), deck.getName(), deck.getHasPendingCards(), deck.getCreatedAt(), deck.getUpdatedAt());

            Window<Deck> deckWindow = Window.from(
                List.of(deck),
                i -> ScrollPosition.keyset(),
                false
            );

            when(deckRepo.findBy(
                ArgumentMatchers.<Specification<Deck>>any(),
                any()
            )).thenReturn(deckWindow);
            when(deckMapper.toResponse(deck)).thenReturn(resp);

            Window<DeckResponse> result = adminService.getUserDecks(USER_ID, request);

            assertThat(result.getContent()).hasSize(1);

            DeckResponse first = result.getContent().getFirst();
            assertThat(first.name()).isEqualTo("Spanish");
            assertThat(first.hasPendingCards()).isTrue();
        }

        @Test
        @DisplayName("should return empty window when user has no decks")
        void shouldReturnEmptyList() {
            Window<Deck> empty = Window.from(
                List.of(),
                i -> ScrollPosition.keyset(),
                false
            );

            when(deckRepo.findBy(
                ArgumentMatchers.<Specification<Deck>>any(),
                any()
            )).thenReturn(empty);

            Window<DeckResponse> result = adminService.getUserDecks(USER_ID, request);

            assertThat(result.getContent()).isEmpty();
        }
    }

    @Nested
    @DisplayName("getDeckCards")
    class GetDeckCards {

        private final CursorPaginationRequest request =
            CursorPaginationRequest.forDecks(null, null, 15, Sort.Direction.DESC);

        @Test
        @DisplayName("should return all cards for given deck with correct fields")
        void shouldReturnDeckCards() {
            User owner = createUser(USER_ID, "alice");
            Deck deck = createDeck(DECK_ID, "Spanish", owner);
            Card card = createCard(CARD_ID, "hola", deck);
            card.setBack("hello");
            CardResponse resp = new CardResponse(card.getId(), card.getFront(), card.getBack(), card.getNextReviewDate());

            Window<Card> cardWindow = Window.from(
                List.of(card),
                i -> ScrollPosition.keyset(),
                false
            );

            when(cardRepo.findBy(
                ArgumentMatchers.<Specification<Card>>any(),
                any()
            )).thenReturn(cardWindow);
            when(cardMapper.toResponse(card)).thenReturn(resp);

            Window<CardResponse> result = adminService.getDeckCards(DECK_ID, request);

            assertThat(result.getContent()).hasSize(1);

            CardResponse first = result.getContent().getFirst();
            assertThat(first.front()).isEqualTo("hola");
            assertThat(first.back()).isEqualTo("hello");
        }

        @Test
        @DisplayName("should return empty window when deck has no cards")
        void shouldReturnEmptyList() {
            Window<Card> empty = Window.from(
                List.of(),
                i -> ScrollPosition.keyset(),
                false
            );

            when(cardRepo.findBy(
                ArgumentMatchers.<Specification<Card>>any(),
                any()
            )).thenReturn(empty);

            Window<CardResponse> result = adminService.getDeckCards(DECK_ID, request);

            assertThat(result.getContent()).isEmpty();
        }
    }

    @Nested
    @DisplayName("getCard")
    class GetCard {

        @Test
        @DisplayName("should return card by id")
        void shouldReturnCard() {
            User owner = createUser(USER_ID, "alice");
            Deck deck = createDeck(DECK_ID, "Spanish", owner);
            Card card = createCard(CARD_ID, "hola", deck);
            CardResponse resp = new CardResponse(card.getId(), card.getFront(), card.getBack(), card.getNextReviewDate());

            when(cardRepo.findById(CARD_ID)).thenReturn(Optional.of(card));
            when(cardMapper.toResponse(card)).thenReturn(resp);

            CardResponse result = adminService.getCard(CARD_ID);

            assertThat(result.id()).isEqualTo(CARD_ID);
            assertThat(result.front()).isEqualTo("hola");
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when card not found")
        void shouldThrowWhenNotFound() {
            when(cardRepo.findById(CARD_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> adminService.getCard(CARD_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(CARD_ID.toString());
        }
    }

    // ======================== DELETE ========================

    @Nested
    @DisplayName("deleteUser")
    class DeleteUser {

        @Test
        @DisplayName("should delete user when it exists")
        void shouldDeleteUser() {
            when(userRepo.existsById(USER_ID)).thenReturn(true);

            adminService.deleteUser(USER_ID);

            verify(userRepo).deleteById(USER_ID);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when user not found")
        void shouldThrowWhenNotFound() {
            when(userRepo.existsById(USER_ID)).thenReturn(false);

            assertThatThrownBy(() -> adminService.deleteUser(USER_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(USER_ID.toString());

            verify(userRepo, never()).deleteById(any());
        }
    }

    @Nested
    @DisplayName("deleteDeck")
    class DeleteDeck {

        @Test
        @DisplayName("should delete deck when it exists")
        void shouldDeleteDeck() {
            when(deckRepo.existsById(DECK_ID)).thenReturn(true);

            adminService.deleteDeck(DECK_ID);

            verify(deckRepo).deleteById(DECK_ID);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when deck not found")
        void shouldThrowWhenNotFound() {
            when(deckRepo.existsById(DECK_ID)).thenReturn(false);

            assertThatThrownBy(() -> adminService.deleteDeck(DECK_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(DECK_ID.toString());

            verify(deckRepo, never()).deleteById(any());
        }
    }

    @Nested
    @DisplayName("deleteCard")
    class DeleteCard {

        @Test
        @DisplayName("should delete card and keep hasPendingCards true when more pending exist")
        void shouldDeleteCard() {
            User owner = createUser(USER_ID, "alice");
            Deck deck = createDeck(DECK_ID, "Spanish", owner);
            deck.setHasPendingCards(true);
            Card card = createCard(CARD_ID, "hola", deck);

            when(cardRepo.findById(CARD_ID)).thenReturn(Optional.of(card));
            when(cardRepo.existsByDeckIdAndNextReviewDateBefore(anyLong(), any())).thenReturn(true);

            adminService.deleteCard(CARD_ID);

            verify(cardRepo).delete(card);
            assertThat(deck.getHasPendingCards()).isTrue();
        }

        @Test
        @DisplayName("should set hasPendingCards false when no more pending cards")
        void shouldSetHasPendingCardsFalseWhenNoMorePending() {
            User owner = createUser(USER_ID, "alice");
            Deck deck = createDeck(DECK_ID, "Spanish", owner);
            deck.setHasPendingCards(true);
            Card card = createCard(CARD_ID, "hola", deck);

            when(cardRepo.findById(CARD_ID)).thenReturn(Optional.of(card));
            when(cardRepo.existsByDeckIdAndNextReviewDateBefore(anyLong(), any())).thenReturn(false);

            adminService.deleteCard(CARD_ID);

            verify(cardRepo).delete(card);
            assertThat(deck.getHasPendingCards()).isFalse();
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when card not found")
        void shouldThrowWhenNotFound() {
            when(cardRepo.findById(CARD_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> adminService.deleteCard(CARD_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(CARD_ID.toString());

            verify(cardRepo, never()).delete(any(Card.class));
        }
    }

    @Nested
    @DisplayName("sendNotification")
    class SendNotification {

        @BeforeEach
        void setUp() {
            when(clock.instant()).thenReturn(NOW);
        }

        @Test
        @DisplayName("should send review reminder email when user exists")
        void shouldSendEmail() {
            User user = createUser(USER_ID, "alice");
            when(userRepo.findById(USER_ID)).thenReturn(Optional.of(user));

            adminService.sendNotification(USER_ID);

            verify(emailService).sendReviewReminderSync(
                user.getEmail(), user.getUsername(), user.getId(), NOW);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when user not found")
        void shouldThrowWhenUserNotFound() {
            when(userRepo.findById(USER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> adminService.sendNotification(USER_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("User not found");

            verify(emailService, never()).sendReviewReminderSync(any(), any(), any(), any());
        }

        @Test
        @DisplayName("should throw RuntimeException when email delivery fails")
        void shouldThrowWhenEmailFails() {
            User user = createUser(USER_ID, "alice");
            when(userRepo.findById(USER_ID)).thenReturn(Optional.of(user));
            doThrow(new RuntimeException("SMTP error"))
                .when(emailService).sendReviewReminderSync(any(), any(), any(), any());

            assertThatThrownBy(() -> adminService.sendNotification(USER_ID))
                .isInstanceOf(RuntimeException.class);
        }
    }
}
