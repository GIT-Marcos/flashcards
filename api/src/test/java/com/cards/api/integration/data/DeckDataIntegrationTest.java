package com.cards.api.integration.data;

import com.cards.api.config.AuditConfig;
import com.cards.api.dto.request.CursorPaginationRequest;
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
import com.cards.api.specification.DeckSpecification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.ScrollPosition;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Window;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

@DataJpaTest
@Import({TestcontainersConfig.class, AuditConfig.class, JpaTestConfig.class})
class DeckDataIntegrationTest {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private DeckRepository deckRepository;
    @Autowired
    private CardRepository cardRepository;
    @Autowired
    private TestEntityManager entityManager;

    private User testUser;
    private User anotherUser;

    @BeforeEach
    void setUp() {
        deckRepository.deleteAllInBatch();

        testUser = userRepository.save(UserMother.createMinimal(
            UserMother.uniqueUsername("owner", System.nanoTime()),
            UserMother.uniqueEmail("owner", System.nanoTime())
        ));

        anotherUser = userRepository.save(UserMother.createMinimal(
            UserMother.uniqueUsername("other", System.nanoTime()),
            UserMother.uniqueEmail("other", System.nanoTime())
        ));
    }

    @Nested
    @DisplayName("Unique name validation")
    class ValidacionDeUnicos {

        @Test
        @DisplayName("SHOULD fail with DataIntegrityViolation WHEN name duplicated within user")
        void shouldFailWithDataIntegrityViolationWhenDuplicateNameWithinUser() {
            // Given
            String uniqueDeckName = DeckMother.uniqueDeckName("Mathematics");
            deckRepository.save(DeckMother.createWithUser(testUser, uniqueDeckName));

            // When & Then
            Deck duplicateDeck = DeckMother.createWithUser(testUser, uniqueDeckName);
            assertThatThrownBy(() -> deckRepository.save(duplicateDeck))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_decks_user_id_name_lower");
        }

        @Test
        @DisplayName("SHOULD fail with DataIntegrityViolation WHEN name duplicated within user (ignore case)")
        void shouldFailWithDataIntegrityViolationWhenDuplicateNameWithinUserIgnoringCase() {
            // Given
            String baseName = DeckMother.uniqueDeckName("Physics");
            deckRepository.save(DeckMother.createWithUser(testUser, baseName));

            // When & Then
            Deck caseVariantDeck = DeckMother.createWithUser(testUser, baseName.toLowerCase());
            assertThatThrownBy(() -> deckRepository.save(caseVariantDeck))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_decks_user_id_name_lower");
        }

        @Test
        @DisplayName("SHOULD allow WHEN same deck name for different users")
        void shouldAllowSameDeckNameForDifferentUsers() {
            // Given
            String sharedName = "World History";

            // When
            Deck deckA = deckRepository.save(DeckMother.createWithUser(testUser, sharedName));
            Deck deckB = deckRepository.save(DeckMother.createWithUser(anotherUser, sharedName));

            // Then
            assertThat(deckA).isNotNull();
            assertThat(deckA.getId()).isNotNull();
            assertThat(deckB).isNotNull();
            assertThat(deckB.getId()).isNotNull();
            assertThat(deckB.getId()).isNotEqualTo(deckA.getId());
            assertThat(deckRepository.count()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("Null relationship validation")
    class ValidacionDeRelacionesNulas {

        @Test
        @DisplayName("SHOULD fail with DataIntegrityViolation WHEN user relation is null")
        void shouldFailWithDataIntegrityViolationWhenUserRelationIsNull() {
            // Given
            Deck orphanDeck = Deck.builder()
                .name("Orphan Deck")
                .build();
            // user se mantiene en null intencionalmente

            // When & Then
            assertThatThrownBy(() -> deckRepository.save(orphanDeck))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("null value in column \"user_id\"");
        }
    }

    @Nested
    @DisplayName("Cascade delete behavior")
    class ValidacionDeComportamientoBorrado {

        @Test
        @DisplayName("SHOULD delete deck WHEN owner user is deleted (via orphanRemoval)")
        void shouldDeleteDeckWhenUserIsDeleted() {
            // Given: Creamos un usuario específico para el test
            User user = userRepository.save(
                UserMother.createMinimal(
                    UserMother.uniqueUsername("cascadeuser", System.currentTimeMillis()),
                    UserMother.uniqueEmail("cascadeuser", System.currentTimeMillis())
                )
            );

            // Creamos el deck pero SIN guardarlo aún
            String deckName = DeckMother.uniqueDeckName("ToDeleteCascade");
            Deck deck = DeckMother.createWithUser(user, deckName);

            // --- SINCRONIZACIÓN MANUAL (Crucial sin CascadeType) ---
            // Añadimos el deck a la lista del usuario para que Hibernate reconozca la orfandad
            user.getDecks().add(deck);

            // Guardamos el mazo (capturar return por @Version → merge())
            deck = deckRepository.save(deck);

            // Forzamos sincronización y limpiamos el contexto para asegurar que
            // la prueba verifique el comportamiento real de la BD/Hibernate
            entityManager.flush();
            entityManager.clear();

            Long userId = user.getId();
            Long deckId = deck.getId();

            // When: Recuperamos el usuario y lo borramos
            User managedUser = userRepository.findById(userId).orElseThrow();
            userRepository.delete(managedUser);

            // El flush aquí procesará el orphanRemoval de la colección 'decks'
            userRepository.flush();

            // Then: El mazo debe haber desaparecido
            assertThat(userRepository.findById(userId)).isEmpty();
            assertThat(deckRepository.findById(deckId)).isEmpty();
        }
    }

    @Nested
    @DisplayName("Bulk update validation")
    class ValidacionDeActualizacionBulk {

        @Test
        @DisplayName("SHOULD update state WHEN due card exists")
        void shouldUpdateStateWhenDueCardExists() {
            // Given
            Deck deck = deckRepository.save(DeckMother.createWithUser(testUser, DeckMother.uniqueDeckName("Bulk Deck")));

            // Capturamos el instante de referencia para garantizar consistencia en la query
            Instant referenceNow = Instant.now();
            Card dueCard = CardMother.createDueCard(deck, "Front", "Back", referenceNow);
            cardRepository.save(dueCard);

            // Aseguramos estado inicial: deck sin pendientes
            deck.setHasPendingCards(false);
            deckRepository.saveAndFlush(deck);

            // When
            int updatedRows = deckRepository.bulkUpdateHasPendingCardsForUsers(
                List.of(testUser.getId()),
                referenceNow
            );

            // Then
            assertThat(updatedRows).isEqualTo(1);
        }

        @Test
        @DisplayName("SHOULD return deck with correctly updated state WHEN due card exists")
        void shouldReturnDeckWithCorrectlyUpdatedStateWhenDueCardExists() {
            // Given
            Deck deck = deckRepository.save(DeckMother.createWithUser(testUser, DeckMother.uniqueDeckName("State Deck")));

            Instant referenceNow = Instant.now();
            Card dueCard = CardMother.createDueCard(deck, "Front 2", "Back 2", referenceNow);
            cardRepository.save(dueCard);

            // Estado inicial forzado a false
            deck.setHasPendingCards(false);
            deckRepository.saveAndFlush(deck);

            // When
            deckRepository.bulkUpdateHasPendingCardsForUsers(
                List.of(testUser.getId()),
                referenceNow
            );

            entityManager.flush();
            entityManager.clear();

            // Then
            Deck updatedDeck = deckRepository.findById(deck.getId()).orElseThrow();
            assertThat(updatedDeck.getHasPendingCards()).isTrue();
        }
    }

    @Nested
    @DisplayName("User query methods")
    class ValidacionDeConsultasPorUsuario {

        @Test
        @DisplayName("SHOULD find all decks WHEN searched by user ID")
        void shouldFindAllDecksWhenSearchedByUserId() {
            // Given
            deckRepository.save(DeckMother.createWithUser(testUser, "Deck A"));
            deckRepository.save(DeckMother.createWithUser(testUser, "Deck B"));
            deckRepository.save(DeckMother.createWithUser(anotherUser, "Deck C"));

            // When
            List<Deck> userDecks = deckRepository.findAllByUserId(testUser.getId());

            // Then
            assertThat(userDecks).hasSize(2);
            assertThat(userDecks).extracting(Deck::getName).containsExactlyInAnyOrder("Deck A", "Deck B");
        }

        @Test
        @DisplayName("SHOULD find deck WHEN searched by ID and user ID")
        void shouldFindDeckWhenSearchedByIdAndUserId() {
            // Given
            Deck deck = deckRepository.save(DeckMother.createWithUser(testUser, DeckMother.uniqueDeckName("Find By ID")));

            // When
            Optional<Deck> found = deckRepository.findByIdAndUserId(deck.getId(), testUser.getId());

            // Then
            assertThat(found).isPresent();
            assertThat(found.get().getId()).isEqualTo(deck.getId());
            assertThat(found.get().getName()).isEqualTo(deck.getName());
            assertThat(found.get().getUser().getId()).isEqualTo(testUser.getId());
        }

        @Test
        @DisplayName("SHOULD return true WHEN deck exists by ID and user ID")
        void shouldReturnTrueWhenDeckExistsByIdAndUserId() {
            // Given
            Deck deck = deckRepository.save(DeckMother.createWithUser(testUser, DeckMother.uniqueDeckName("Exists ID")));

            // When
            boolean exists = deckRepository.existsByIdAndUserId(deck.getId(), testUser.getId());

            // Then
            assertThat(exists).isTrue();
        }

        @Test
        @DisplayName("SHOULD return empty WHEN deck ID does not belong to user")
        void shouldReturnEmptyWhenDeckIdDoesNotBelongToUser() {
            Deck deck = deckRepository.save(DeckMother.createWithUser(anotherUser, DeckMother.uniqueDeckName("Other User Deck")));

            Optional<Deck> found = deckRepository.findByIdAndUserId(deck.getId(), testUser.getId());

            assertThat(found).isEmpty();
        }
    }

    @Nested
    @DisplayName("Name existence validation")
    class ValidacionDeExistenciaPorNombre {

        @Test
        @DisplayName("SHOULD return true WHEN deck exists by name and user ID")
        void shouldReturnTrueWhenDeckExistsByNameAndUserId() {
            // Given
            String deckName = DeckMother.uniqueDeckName("Exact Case Deck");
            deckRepository.save(DeckMother.createWithUser(testUser, deckName));

            // When
            boolean exists = deckRepository.existsByUserIdAndNameIgnoreCase(testUser.getId(), deckName);

            // Then
            assertThat(exists).isTrue();
        }

        @Test
        @DisplayName("SHOULD return true WHEN deck exists by name (different case) and user ID")
        void shouldReturnTrueWhenDeckExistsByNameDifferentCaseAndUserId() {
            // Given
            String deckName = "MixedCase Deck";
            deckRepository.save(DeckMother.createWithUser(testUser, deckName));

            // When
            boolean exists = deckRepository.existsByUserIdAndNameIgnoreCase(testUser.getId(), deckName.toLowerCase());

            // Then
            assertThat(exists).isTrue();
        }
    }

    @Nested
    @DisplayName("Deck Pagination: Keyset Scrolling")
    class DeckPaginationAndScrolling {

        @Test
        @DisplayName("should scroll correctly even when multiple decks share the same timestamp")
        void shouldScrollWithSharedTimestamps() {
            // Given: 3 decks con el MISMO createdAt exacto para forzar desempate por ID
            Instant sharedInstant = Instant.parse("2026-05-06T10:00:00Z");
            deckRepository.save(DeckMother.createWithDate(testUser, "Deck 1", sharedInstant));
            deckRepository.save(DeckMother.createWithDate(testUser, "Deck 2", sharedInstant));
            deckRepository.save(DeckMother.createWithDate(testUser, "Deck 3", sharedInstant));
            deckRepository.flush();

            CursorPaginationRequest pdr = CursorPaginationRequest.forDecks(null, null, 2, Sort.Direction.ASC);
            Specification<Deck> spec = DeckSpecification.getFromUser(testUser.getId());

            // Página 1 (Size 2)
            Window<Deck> firstWindow = deckRepository.findBy(spec, q -> q
                .limit(pdr.pageSize()).sortBy(pdr.toSort()).scroll(pdr.toScrollPosition()));

            // Página 2 (Desde el último de la P1)
            ScrollPosition lastPos = firstWindow.positionAt(firstWindow.getContent().size() - 1);
            Window<Deck> secondWindow = deckRepository.findBy(spec, q -> q
                .limit(pdr.pageSize()).sortBy(pdr.toSort()).scroll(lastPos));

            // Then
            assertSoftly(softly -> {
                softly.assertThat(firstWindow.getContent()).hasSize(2);
                softly.assertThat(secondWindow.getContent()).hasSize(1);
                softly.assertThat(secondWindow.getContent().getFirst().getName()).isEqualTo("Deck 3");
            });
        }

        @Test
        @DisplayName("should filter only decks that have pending cards")
        void shouldFilterDecksWithPendingCardsOnly() {
            // Given
            Deck pendingDeck = deckRepository.save(DeckMother.createPending(testUser, "Mazo con Pendientes"));
            Card dueCard = CardMother.createDueCard(pendingDeck, "Q", "A", Instant.now());
            cardRepository.save(dueCard);
            deckRepository.save(DeckMother.createWithUser(testUser, "Mazo Al Día"));
            deckRepository.flush();

            // When
            CursorPaginationRequest pdr = CursorPaginationRequest.forDecks(null, null, 10, Sort.Direction.ASC);
            Specification<Deck> spec = DeckSpecification.getFromUserAndPendingCards(testUser.getId(), Instant.now());

            Window<Deck> window = deckRepository.findBy(spec, q -> q
                .limit(pdr.pageSize()).sortBy(pdr.toSort()).scroll(pdr.toScrollPosition()));

            // Then
            assertThat(window.getContent())
                .hasSize(1)
                .extracting(Deck::getName)
                .containsExactly("Mazo con Pendientes");
        }

        @Test
        @DisplayName("should handle reverse scrolling (DESC order)")
        void shouldHandleReverseScrolling() {
            // Given
            Instant now = Instant.now();
            deckRepository.save(DeckMother.createWithDate(testUser, "Old", now.minusSeconds(100)));
            deckRepository.save(DeckMother.createWithDate(testUser, "New", now));
            deckRepository.flush();

            CursorPaginationRequest pdr = CursorPaginationRequest.forDecks(null, null, 10, Sort.Direction.DESC);

            Window<Deck> window = deckRepository.findBy(DeckSpecification.getFromUser(testUser.getId()), q -> q
                .limit(pdr.pageSize()).sortBy(pdr.toSort()).scroll(pdr.toScrollPosition()));

            // Then
            assertThat(window.getContent()).extracting(Deck::getName).containsExactly("New", "Old");
        }
    }
}
