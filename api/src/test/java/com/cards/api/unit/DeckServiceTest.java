package com.cards.api.unit;

import com.cards.api.dto.request.CreateDeckRequest;
import com.cards.api.dto.request.CursorPaginationRequest;
import com.cards.api.dto.request.PatchDeckRequest;
import com.cards.api.dto.response.DeckResponse;
import com.cards.api.entity.Deck;
import com.cards.api.entity.User;
import com.cards.api.exception.ResourceNotFoundException;
import com.cards.api.exception.domain.DuplicatedDeckException;
import com.cards.api.mapper.DeckMapper;
import com.cards.api.repo.DeckRepository;
import com.cards.api.repo.UserRepository;
import com.cards.api.service.DeckService;
import io.micrometer.core.instrument.Counter;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DeckService")
class DeckServiceTest {

    @Mock
    private DeckRepository deckRepo;
    @Mock
    private UserRepository userRepo;
    @Mock
    private DeckMapper deckMapper;
    @Mock
    private Counter decksCreatedCounter;
    @Mock
    private Clock clock;

    private DeckService deckService;

    private static final Long USER_ID = 1L;
    private static final Long DECK_ID = 10L;

    @BeforeEach
    void setUp() {
        deckService = new DeckService(deckRepo, userRepo, deckMapper, decksCreatedCounter, clock);
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

    private Deck createDeck(Long id, String name) {
        Deck deck = Deck.builder()
            .name(name)
            .user(createOwner())
            .build();
        deck.setId(id);
        return deck;
    }

    private DeckResponse createDeckResponse(Long id, String name, Instant createdAt, Instant updatedAt, boolean hasPendingCards) {
        return new DeckResponse(id, name, hasPendingCards, createdAt, updatedAt);
    }

    // ======================== CREATE ========================

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("should create deck and return response")
        void shouldCreateDeck() {
            CreateDeckRequest request = new CreateDeckRequest("Spanish");
            User owner = createOwner();
            Deck saved = createDeck(DECK_ID, "Spanish");
            DeckResponse expected = createDeckResponse(DECK_ID, "Spanish", saved.getCreatedAt(), saved.getUpdatedAt(), false);

            when(userRepo.getReferenceById(USER_ID)).thenReturn(owner);
            when(deckRepo.existsByUserIdAndNameIgnoreCase(USER_ID, "Spanish")).thenReturn(false);
            when(deckMapper.toEntity(owner, request)).thenReturn(createDeck(null, "Spanish"));
            when(deckRepo.save(any(Deck.class))).thenReturn(saved);
            when(deckMapper.toResponse(saved)).thenReturn(expected);

            DeckResponse response = deckService.create(USER_ID, request);

            assertThat(response.id()).isEqualTo(DECK_ID);
            assertThat(response.name()).isEqualTo("Spanish");
            verify(decksCreatedCounter).increment();
        }

        @Test
        @DisplayName("should throw DuplicatedDeckException when name already exists")
        void shouldThrowForDuplicatedName() {
            CreateDeckRequest request = new CreateDeckRequest("Spanish");

            when(userRepo.getReferenceById(USER_ID)).thenReturn(createOwner());
            when(deckRepo.existsByUserIdAndNameIgnoreCase(USER_ID, "Spanish")).thenReturn(true);

            assertThatThrownBy(() -> deckService.create(USER_ID, request))
                .isInstanceOf(DuplicatedDeckException.class);
            verifyNoInteractions(deckMapper);
            verify(decksCreatedCounter, never()).increment();
        }

        @Test
        @DisplayName("should catch DataIntegrityViolationException and throw DuplicatedDeckException")
        void shouldCatchDataIntegrityViolation() {
            CreateDeckRequest request = new CreateDeckRequest("Spanish");

            when(userRepo.getReferenceById(USER_ID)).thenReturn(createOwner());
            when(deckRepo.existsByUserIdAndNameIgnoreCase(USER_ID, "Spanish")).thenReturn(false);
            when(deckMapper.toEntity(any(User.class), any(CreateDeckRequest.class)))
                .thenReturn(createDeck(null, "Spanish"));
            when(deckRepo.save(any(Deck.class)))
                .thenThrow(new DataIntegrityViolationException("unique"));

            assertThatThrownBy(() -> deckService.create(USER_ID, request))
                .isInstanceOf(DuplicatedDeckException.class);
            verify(decksCreatedCounter, never()).increment();
        }
    }

    // ======================== PATCH ========================

    @Nested
    @DisplayName("patch")
    class Patch {

        @Test
        @DisplayName("should update deck name successfully")
        void shouldUpdateName() {
            PatchDeckRequest request = new PatchDeckRequest("French");
            Deck existing = createDeck(DECK_ID, "Spanish");
            Deck updated = createDeck(DECK_ID, "French");
            DeckResponse expected = createDeckResponse(DECK_ID, "French", updated.getCreatedAt(), updated.getUpdatedAt(), false);

            when(deckRepo.findByIdAndUserId(DECK_ID, USER_ID)).thenReturn(Optional.of(existing));
            when(deckRepo.existsByUserIdAndNameIgnoreCase(USER_ID, "French")).thenReturn(false);
            when(deckMapper.patchEntity(existing, request)).thenReturn(updated);
            when(deckRepo.save(any(Deck.class))).thenReturn(updated);
            when(deckMapper.toResponse(updated)).thenReturn(expected);

            DeckResponse response = deckService.patch(DECK_ID, USER_ID, request);

            assertThat(response.name()).isEqualTo("French");
        }

        @Test
        @DisplayName("should return unchanged deck when name is null")
        void shouldReturnUnchangedWhenNameNull() {
            PatchDeckRequest request = new PatchDeckRequest(null);
            Deck existing = createDeck(DECK_ID, "Spanish");
            DeckResponse expected = createDeckResponse(DECK_ID, "Spanish", existing.getCreatedAt(), existing.getUpdatedAt(), false);

            when(deckRepo.findByIdAndUserId(DECK_ID, USER_ID)).thenReturn(Optional.of(existing));
            when(deckMapper.toResponse(existing)).thenReturn(expected);

            DeckResponse response = deckService.patch(DECK_ID, USER_ID, request);

            assertThat(response.name()).isEqualTo("Spanish");
            verify(deckRepo, never()).save(any());
        }

        @Test
        @DisplayName("should return unchanged deck when name has not changed")
        void shouldReturnUnchangedWhenSameName() {
            PatchDeckRequest request = new PatchDeckRequest("Spanish");
            Deck existing = createDeck(DECK_ID, "Spanish");
            DeckResponse expected = createDeckResponse(DECK_ID, "Spanish", existing.getCreatedAt(), existing.getUpdatedAt(), false);

            when(deckRepo.findByIdAndUserId(DECK_ID, USER_ID)).thenReturn(Optional.of(existing));
            when(deckMapper.toResponse(existing)).thenReturn(expected);

            DeckResponse response = deckService.patch(DECK_ID, USER_ID, request);

            assertThat(response.name()).isEqualTo("Spanish");
            verify(deckRepo, never()).save(any());
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when deck not found")
        void shouldThrowWhenDeckNotFound() {
            PatchDeckRequest request = new PatchDeckRequest("French");

            when(deckRepo.findByIdAndUserId(DECK_ID, USER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> deckService.patch(DECK_ID, USER_ID, request))
                .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("should throw DuplicatedDeckException when new name already exists")
        void shouldThrowForDuplicatedName() {
            PatchDeckRequest request = new PatchDeckRequest("French");
            Deck existing = createDeck(DECK_ID, "Spanish");

            when(deckRepo.findByIdAndUserId(DECK_ID, USER_ID)).thenReturn(Optional.of(existing));
            when(deckRepo.existsByUserIdAndNameIgnoreCase(USER_ID, "French")).thenReturn(true);

            assertThatThrownBy(() -> deckService.patch(DECK_ID, USER_ID, request))
                .isInstanceOf(DuplicatedDeckException.class);
        }
    }

    // ======================== DELETE ========================

    @Nested
    @DisplayName("delete")
    class Delete {

        @Test
        @DisplayName("should delete deck when it belongs to user")
        void shouldDeleteWhenDeckBelongsToUser() {
            when(deckRepo.existsByIdAndUserId(DECK_ID, USER_ID)).thenReturn(true);

            deckService.delete(DECK_ID, USER_ID);

            verify(deckRepo).deleteById(DECK_ID);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when deck does not belong to user")
        void shouldThrowWhenDeckDoesNotBelongToUser() {
            when(deckRepo.existsByIdAndUserId(DECK_ID, USER_ID)).thenReturn(false);

            assertThatThrownBy(() -> deckService.delete(DECK_ID, USER_ID))
                .isInstanceOf(ResourceNotFoundException.class);

            verify(deckRepo, never()).deleteById(anyLong());
        }
    }

    // ======================== READ ========================

    @Nested
    @DisplayName("getDecks")
    class GetDecks {

        @Test
        @DisplayName("should return paginated decks for user")
        void shouldReturnUserDecks() {

            CursorPaginationRequest request =
                CursorPaginationRequest.forDecks(null, null, 15, Sort.Direction.DESC);

            Deck deck = createDeck(DECK_ID, "Spanish");
            DeckResponse response = createDeckResponse(DECK_ID, "Spanish", deck.getCreatedAt(), deck.getUpdatedAt(), false);

            Window<Deck> deckWindow = Window.from(
                List.of(deck),
                i -> ScrollPosition.keyset(),
                true
            );

            when(deckRepo.findBy(
                ArgumentMatchers.<Specification<Deck>>any(),
                any()
            )).thenReturn(deckWindow);

            when(deckMapper.toResponse(deck)).thenReturn(response);

            Window<DeckResponse> result = deckService.getDecks(USER_ID, request);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().getFirst().name())
                .isEqualTo("Spanish");
        }
    }

    @Nested
    @DisplayName("getDueDecks")
    class GetDueDecks {

        @Test
        @DisplayName("should return only decks with pending cards")
        void shouldReturnDueDecks() {
            CursorPaginationRequest request = CursorPaginationRequest.forDecks(null, null, 15, Sort.Direction.DESC);
            Deck deck = createDeck(DECK_ID, "Spanish");
            deck.setHasPendingCards(true);
            deck.setHasPendingCards(true);
            DeckResponse response = createDeckResponse(DECK_ID, "Spanish", deck.getCreatedAt(), deck.getUpdatedAt(), true);
            Window<Deck> deckWindow = Window.from(
                List.of(deck),
                i -> ScrollPosition.keyset(),
                true
            );

            when(deckRepo.findBy(
                ArgumentMatchers.<Specification<Deck>>any(),
                any()
            )).thenReturn(deckWindow);
            when(deckMapper.toResponse(deck)).thenReturn(response);

            Window<DeckResponse> result = deckService.getDueDecks(USER_ID, request);

            assertThat(result.getContent()).hasSize(1);
        }
    }

    // ======================== ADMIN OPERATIONS ========================

    @Nested
    @DisplayName("updatePendingFlagsForUsers")
    class UpdatePendingFlags {

        @Test
        @DisplayName("should delegate to repository bulk update")
        void shouldDelegateToBulkUpdate() {
            List<Long> userIds = List.of(1L, 2L, 3L);
            Instant now = Instant.now();

            deckService.updatePendingFlagsForUsers(userIds, now);

            verify(deckRepo).bulkUpdateHasPendingCardsForUsers(userIds, now);
        }
    }
}
