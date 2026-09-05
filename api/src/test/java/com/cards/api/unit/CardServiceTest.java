package com.cards.api.unit;

import com.cards.api.dto.request.CreateCardRequest;
import com.cards.api.dto.request.CursorPaginationRequest;
import com.cards.api.dto.request.PatchCardRequest;
import com.cards.api.dto.response.CardResponse;
import com.cards.api.entity.Card;
import com.cards.api.entity.Deck;
import com.cards.api.entity.User;
import com.cards.api.exception.ResourceNotFoundException;
import com.cards.api.exception.domain.DuplicatedCardException;
import com.cards.api.mapper.CardMapper;
import com.cards.api.repo.CardRepository;
import com.cards.api.repo.DeckRepository;
import com.cards.api.service.CardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.ScrollPosition;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Window;
import org.springframework.data.jpa.domain.Specification;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static java.time.Instant.now;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CardService")
class CardServiceTest {

    @Mock
    private CardRepository cardRepo;
    @Mock
    private DeckRepository deckRepo;
    @Mock
    private CardMapper cardMapper;
    @Mock
    private Clock clock;

    private CardService cardService;

    private static final Long USER_ID = 1L;
    private static final Long DECK_ID = 10L;
    private static final Long CARD_ID = 100L;

    @BeforeEach
    void setUp() {
        cardService = new CardService(cardRepo, deckRepo, cardMapper, clock);
    }

    // ======================== HELPERS ========================

    private User createOwner() {
        User user = User.builder()
            .username("owner")
            .email("owner@email.com")
            .passwordHash("hash")
            .zoneInfo("UTC")
            .addRole(User.UserRole.ROLE_USER)
            .build();
        user.setId(USER_ID);
        return user;
    }

    private Deck createOwnedDeck() {
        Deck deck = Deck.builder()
            .name("Spanish")
            .user(createOwner())
            .build();
        deck.setId(DECK_ID);
        return deck;
    }

    private Card createCard(Long id, String front, String back) {
        Card card = Card.builder()
            .front(front)
            .back(back)
            .deck(createOwnedDeck())
            .build();
        card.setId(id);
        card.setNextReviewDate(Instant.now());
        return card;
    }

    private CardResponse createCardResponse(Long id, String front, String back, Instant nextReviewDate) {
        return new CardResponse(id, front, back, nextReviewDate);
    }

    // ======================== CREATE ========================

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("should create card and return response")
        void shouldCreateCard() {
            CreateCardRequest request = new CreateCardRequest("hola", "hello");
            Deck deck = createOwnedDeck();
            Card saved = createCard(CARD_ID, "hola", "hello");
            CardResponse expected = createCardResponse(CARD_ID, "hola", "hello", now());

            when(deckRepo.findByIdAndUserId(DECK_ID, USER_ID)).thenReturn(Optional.of(deck));
            when(cardRepo.existsByFrontIgnoreCaseAndDeckId("hola", DECK_ID)).thenReturn(false);
            when(cardMapper.toEntity(any(Deck.class), any(CreateCardRequest.class), any())).thenReturn(createCard(null, "hola", "hello"));

            when(cardRepo.save(any(Card.class))).thenReturn(saved);
            when(cardMapper.toResponse(saved)).thenReturn(expected);

            CardResponse response = cardService.create(DECK_ID, USER_ID, request);

            assertThat(response).isNotNull();
            assertThat(response.id()).isEqualTo(CARD_ID);
            assertThat(response.front()).isEqualTo("hola");
            assertThat(response.back()).isEqualTo("hello");
            assertThat(deck.getHasPendingCards()).isTrue();
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when deck does not belong to user")
        void shouldThrowWhenDeckNotFound() {
            CreateCardRequest request = new CreateCardRequest("hola", "hello");

            when(deckRepo.findByIdAndUserId(DECK_ID, USER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> cardService.create(DECK_ID, USER_ID, request))
                .isInstanceOf(ResourceNotFoundException.class);

            verifyNoInteractions(cardRepo);
        }

        @Test
        @DisplayName("should throw DuplicatedCardException when front already exists in deck")
        void shouldThrowForDuplicatedFront() {
            CreateCardRequest request = new CreateCardRequest("hola", "hello");
            Deck deck = createOwnedDeck();

            when(deckRepo.findByIdAndUserId(DECK_ID, USER_ID)).thenReturn(Optional.of(deck));
            when(cardRepo.existsByFrontIgnoreCaseAndDeckId("hola", DECK_ID)).thenReturn(true);

            assertThatThrownBy(() -> cardService.create(DECK_ID, USER_ID, request))
                .isInstanceOf(DuplicatedCardException.class);
        }

        @Test
        @DisplayName("should catch DataIntegrityViolationException and throw DuplicatedCardException")
        void shouldCatchDataIntegrityViolation() {
            CreateCardRequest request = new CreateCardRequest("hola", "hello");
            Deck deck = createOwnedDeck();

            when(deckRepo.findByIdAndUserId(DECK_ID, USER_ID)).thenReturn(Optional.of(deck));
            when(cardRepo.existsByFrontIgnoreCaseAndDeckId("hola", DECK_ID)).thenReturn(false);
            when(cardMapper.toEntity(any(Deck.class), any(CreateCardRequest.class), any())).thenReturn(createCard(null, "hola", "hello"));
            when(cardRepo.save(any(Card.class))).thenThrow(new DataIntegrityViolationException("unique"));

            assertThatThrownBy(() -> cardService.create(DECK_ID, USER_ID, request))
                .isInstanceOf(DuplicatedCardException.class);
        }
    }

    // ======================== PATCH ========================

    @Nested
    @DisplayName("patch")
    class Patch {

        @Test
        @DisplayName("should update front successfully")
        void shouldUpdateFront() {
            PatchCardRequest request = new PatchCardRequest("adios", null);
            Card existing = createCard(CARD_ID, "hola", "hello");
            Card updated = createCard(CARD_ID, "adios", "hello");
            CardResponse expected = createCardResponse(CARD_ID, "adios", "hello", now());

            when(cardRepo.findByIdAndDeck_User_Id(CARD_ID, USER_ID)).thenReturn(Optional.of(existing));
            when(cardRepo.existsByFrontIgnoreCaseAndDeckId("adios", DECK_ID)).thenReturn(false);
            when(cardMapper.patchEntity(existing, request)).thenReturn(updated);
            when(cardRepo.save(any(Card.class))).thenReturn(updated);
            when(cardMapper.toResponse(updated)).thenReturn(expected);

            CardResponse response = cardService.patch(CARD_ID, USER_ID, request);

            assertThat(response.front()).isEqualTo("adios");
        }

        @Test
        @DisplayName("should update back successfully")
        void shouldUpdateBack() {
            PatchCardRequest request = new PatchCardRequest(null, "goodbye");
            Card existing = createCard(CARD_ID, "hola", "hello");
            Card updated = createCard(CARD_ID, "hola", "goodbye");
            CardResponse expected = createCardResponse(CARD_ID, "hola", "goodbye", now());

            when(cardRepo.findByIdAndDeck_User_Id(CARD_ID, USER_ID)).thenReturn(Optional.of(existing));
            when(cardMapper.patchEntity(existing, request)).thenReturn(updated);
            when(cardRepo.save(any(Card.class))).thenReturn(updated);
            when(cardMapper.toResponse(updated)).thenReturn(expected);

            CardResponse response = cardService.patch(CARD_ID, USER_ID, request);

            assertThat(response.back()).isEqualTo("goodbye");
        }

        @Test
        @DisplayName("should return unchanged card when both fields are null")
        void shouldReturnUnchangedWhenBothNull() {
            PatchCardRequest request = new PatchCardRequest(null, null);
            Card existing = createCard(CARD_ID, "hola", "hello");
            CardResponse expected = createCardResponse(CARD_ID, "hola", "hello", now());

            when(cardRepo.findByIdAndDeck_User_Id(CARD_ID, USER_ID)).thenReturn(Optional.of(existing));
            when(cardMapper.toResponse(existing)).thenReturn(expected);

            CardResponse response = cardService.patch(CARD_ID, USER_ID, request);

            assertThat(response.front()).isEqualTo("hola");
            assertThat(response.back()).isEqualTo("hello");
            verify(cardRepo, never()).save(any());
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when card not found")
        void shouldThrowWhenCardNotFound() {
            PatchCardRequest request = new PatchCardRequest("new", null);

            when(cardRepo.findByIdAndDeck_User_Id(CARD_ID, USER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> cardService.patch(CARD_ID, USER_ID, request))
                .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("should throw DuplicatedCardException when new front already exists in deck")
        void shouldThrowForDuplicatedFrontOnPatch() {
            PatchCardRequest request = new PatchCardRequest("duplicate", null);
            Card existing = createCard(CARD_ID, "hola", "hello");

            when(cardRepo.findByIdAndDeck_User_Id(CARD_ID, USER_ID)).thenReturn(Optional.of(existing));
            when(cardRepo.existsByFrontIgnoreCaseAndDeckId("duplicate", DECK_ID)).thenReturn(true);

            assertThatThrownBy(() -> cardService.patch(CARD_ID, USER_ID, request))
                .isInstanceOf(DuplicatedCardException.class);
        }

        @Test
        @DisplayName("should allow patching with same front value without duplication check")
        void shouldAllowSameFrontWithoutDupCheck() {
            PatchCardRequest request = new PatchCardRequest("hola", null);
            Card existing = createCard(CARD_ID, "hola", "hello");
            Card updated = createCard(CARD_ID, "hola", "hello");
            CardResponse expected = createCardResponse(CARD_ID, "hola", "hello", now());

            when(cardRepo.findByIdAndDeck_User_Id(CARD_ID, USER_ID)).thenReturn(Optional.of(existing));
            when(cardMapper.patchEntity(existing, request)).thenReturn(updated);
            when(cardRepo.save(any(Card.class))).thenReturn(updated);
            when(cardMapper.toResponse(updated)).thenReturn(expected);

            CardResponse response = cardService.patch(CARD_ID, USER_ID, request);

            assertThat(response.front()).isEqualTo("hola");
            verify(cardRepo, never()).existsByFrontIgnoreCaseAndDeckId(any(), anyLong());
        }
    }

    // ======================== DELETE ========================

    @Nested
    @DisplayName("delete")
    class Delete {

        @Test
        @DisplayName("should delete card and keep hasPendingCards true when more pending exist")
        void shouldDeleteCard() {
            Card card = createCard(CARD_ID, "hola", "hello");
            Deck deck = card.getDeck();
            deck.setHasPendingCards(true);

            when(cardRepo.findByIdAndDeck_User_Id(CARD_ID, USER_ID)).thenReturn(Optional.of(card));
            when(cardRepo.existsByDeckIdAndNextReviewDateBefore(anyLong(), any())).thenReturn(true);

            cardService.delete(CARD_ID, USER_ID);

            verify(cardRepo).delete(card);
            assertThat(deck.getHasPendingCards()).isTrue();
        }

        @Test
        @DisplayName("should set hasPendingCards false when no more pending cards")
        void shouldSetHasPendingCardsFalseWhenNoMorePending() {
            Card card = createCard(CARD_ID, "hola", "hello");
            Deck deck = card.getDeck();
            deck.setHasPendingCards(true);

            when(cardRepo.findByIdAndDeck_User_Id(CARD_ID, USER_ID)).thenReturn(Optional.of(card));
            when(cardRepo.existsByDeckIdAndNextReviewDateBefore(anyLong(), any())).thenReturn(false);

            cardService.delete(CARD_ID, USER_ID);

            verify(cardRepo).delete(card);
            assertThat(deck.getHasPendingCards()).isFalse();
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when card not found")
        void shouldThrowWhenCardNotFound() {
            when(cardRepo.findByIdAndDeck_User_Id(CARD_ID, USER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> cardService.delete(CARD_ID, USER_ID))
                .isInstanceOf(ResourceNotFoundException.class);

            verify(cardRepo, never()).delete(any(Card.class));
        }
    }

    // ======================== READ ========================

    @Nested
    @DisplayName("getById")
    class GetById {

        @Test
        @DisplayName("should return card response")
        void shouldReturnCard() {
            Card card = createCard(CARD_ID, "hola", "hello");
            CardResponse expected = createCardResponse(CARD_ID, "hola", "hello", now());

            when(cardRepo.findByIdAndDeck_User_Id(CARD_ID, USER_ID)).thenReturn(Optional.of(card));
            when(cardMapper.toResponse(card)).thenReturn(expected);

            CardResponse response = cardService.getById(CARD_ID, USER_ID);

            assertThat(response.id()).isEqualTo(CARD_ID);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when card not found")
        void shouldThrowWhenNotFound() {
            when(cardRepo.findByIdAndDeck_User_Id(CARD_ID, USER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> cardService.getById(CARD_ID, USER_ID))
                .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("getByDeck")
    class GetByDeck {

        @Test
        @DisplayName("should return paginated cards for deck with correct fields")
        void shouldReturnPaginatedCards() {
            CursorPaginationRequest request = CursorPaginationRequest.forCards(null, null, 15, Sort.Direction.ASC);
            Card card = createCard(CARD_ID, "hola", "hello");
            CardResponse cardResponse = createCardResponse(CARD_ID, "hola", "hello", now());
            Window<Card> cardWindow = Window.from(
                List.of(card),
                i -> ScrollPosition.keyset(),
                true
            );

            when(deckRepo.existsByIdAndUserId(DECK_ID, USER_ID)).thenReturn(true);
            when(cardRepo.findBy(
                ArgumentMatchers.<Specification<Card>>any(),
                any()
            )).thenReturn(cardWindow);

            when(cardMapper.toResponse(card)).thenReturn(cardResponse);

            Window<CardResponse> result = cardService.getByDeck(DECK_ID, USER_ID, request);

            assertThat(result).isNotNull();
            assertThat(result.getContent()).hasSize(1);

            CardResponse first = result.getContent().getFirst();
            assertThat(first.front()).isEqualTo("hola");
            assertThat(first.back()).isEqualTo("hello");
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when deck does not belong to user")
        void shouldThrowWhenDeckNotFound() {
            CursorPaginationRequest request = CursorPaginationRequest.forCards(null, null, 15, Sort.Direction.ASC);

            when(deckRepo.existsByIdAndUserId(DECK_ID, USER_ID)).thenReturn(false);

            assertThatThrownBy(() -> cardService.getByDeck(DECK_ID, USER_ID, request))
                .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("getPendingByDeck")
    class GetPendingByDeck {

        @Test
        @DisplayName("should return only pending cards")
        void shouldReturnPendingCards() {
            CursorPaginationRequest request = CursorPaginationRequest.forCards(null, null, 15, Sort.Direction.ASC);
            Card card = createCard(CARD_ID, "hola", "hello");
            card.setNextReviewDate(Instant.now().minusSeconds(3600));
            CardResponse cardResponse = createCardResponse(CARD_ID, "hola", "hello", now());
            Window<Card> cardWindow = Window.from(
                List.of(card),
                i -> ScrollPosition.keyset(),
                true
            );

            when(deckRepo.existsByIdAndUserId(DECK_ID, USER_ID)).thenReturn(true);

            when(cardRepo.findBy(
                ArgumentMatchers.<Specification<Card>>any(),
                any()
            )).thenReturn(cardWindow);

            when(cardMapper.toResponse(card)).thenReturn(cardResponse);

            Window<CardResponse> result = cardService.getPendingByDeck(DECK_ID, USER_ID, request);

            assertThat(result.getContent()).hasSize(1);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when deck does not belong to user")
        void shouldThrowWhenDeckNotFound() {
            CursorPaginationRequest request = CursorPaginationRequest.forCards(null, null, 15, Sort.Direction.ASC);

            when(deckRepo.existsByIdAndUserId(DECK_ID, USER_ID)).thenReturn(false);

            assertThatThrownBy(() -> cardService.getPendingByDeck(DECK_ID, USER_ID, request))
                .isInstanceOf(ResourceNotFoundException.class);
        }
    }
}
