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
import com.cards.api.specification.CardSpecifications;
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
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

@DataJpaTest
@Import({TestcontainersConfig.class, AuditConfig.class, JpaTestConfig.class})
class CardDataIntegrationTest {

    @Autowired
    private CardRepository cardRepository;

    @Autowired
    private DeckRepository deckRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TestEntityManager entityManager;

    private User testUser;
    private Deck testDeck;
    private Deck anotherDeck;

    @BeforeEach
    void setUp() {
        userRepository.deleteAllInBatch();

        testUser = userRepository.save(
            UserMother.createMinimal(
                UserMother.uniqueUsername("cardtest", System.currentTimeMillis()),
                UserMother.uniqueEmail("cardtest", System.currentTimeMillis())
            )
        );

        testDeck = deckRepository.save(
            DeckMother.createWithUser(testUser, DeckMother.uniqueDeckName("Test Deck"))
        );

        anotherDeck = deckRepository.save(
            DeckMother.createWithUser(testUser, DeckMother.uniqueDeckName("Another Deck"))
        );
    }

    @Nested
    @DisplayName("Unique validation")
    class UniqueValidationTests {

        @Test
        @DisplayName("SHOULD fail with DataIntegrityViolation WHEN front duplicated in same deck")
        void shouldFailWithDataIntegrityViolationWhenFrontDuplicatedInSameDeck() {
            // Given
            Card card1 = CardMother.createNew(testDeck, "¿Qué es Spring Boot?", "Framework Java");
            Card card2 = CardMother.createNew(testDeck, "¿Qué es Spring Boot?", "Otra respuesta");
            cardRepository.saveAndFlush(card1);

            // When & Then
            assertThatThrownBy(() -> cardRepository.saveAndFlush(card2))
                .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("SHOULD fail with DataIntegrityViolation WHEN front duplicated in same deck (ignore case)")
        void shouldFailWithDataIntegrityViolationWhenFrontDuplicatedInSameDeckIgnoringCase() {
            // Given
            Card card1 = CardMother.createNew(testDeck, "¿Qué es Java?", "Lenguaje OOP");
            Card card2 = CardMother.createNew(testDeck, "¿QUÉ ES JAVA?", "Lenguaje OOP");
            cardRepository.saveAndFlush(card1);

            // When & Then
            assertThatThrownBy(() -> cardRepository.saveAndFlush(card2))
                .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("SHOULD allow WHEN same front in different decks")
        void shouldAllowWhenSameFrontInDifferentDecks() {
            // Given
            Deck anotherDeck = deckRepository.save(
                DeckMother.createWithUser(
                    testUser,
                    DeckMother.uniqueDeckName("Otro Deck")
                )
            );
            Card card1 = CardMother.createNew(testDeck, "¿Qué es Java?", "Lenguaje OOP");
            Card card2 = CardMother.createNew(anotherDeck, "¿Qué es Java?", "Framework Web");
            cardRepository.saveAndFlush(card1);

            // When
            Card savedCard2 = cardRepository.saveAndFlush(card2);

            // Then
            assertThat(savedCard2.getId()).isNotNull();
            assertThat(cardRepository.count()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("Null relationship validation")
    class NullRelationshipValidationTests {

        @Test
        @DisplayName("SHOULD fail with DataIntegrityViolation WHEN deck relationship is null")
        void shouldFailWithDataIntegrityViolationWhenDeckRelationshipIsNull() {
            // Given
            Card cardWithoutDeck = Card.builder()
                .front("Pregunta huérfana")
                .back("Respuesta huérfana")
                .easinessFactor(2.5)
                .intervalDays(0)
                .repetitionCount(0)
                .build();
            // deck se mantiene null intencionalmente

            // When & Then
            assertThatThrownBy(() -> cardRepository.saveAndFlush(cardWithoutDeck))
                .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Nested
    @DisplayName("Cascade delete behavior")
    class CascadeDeleteBehaviorTests {

        @Test
        @DisplayName("SHOULD cascade delete all cards WHEN deck is deleted")
        void shouldCascadeDeleteAllCardsWhenDeckIsDeleted() {
            // Given: Specific user for isolation
            User user = userRepository.save(
                UserMother.createMinimal(
                    UserMother.uniqueUsername("cascadecards", System.currentTimeMillis()),
                    UserMother.uniqueEmail("cascadecards", System.currentTimeMillis())
                )
            );

            // Create the deck but DON'T save yet
            String deckName = DeckMother.uniqueDeckName("ToDeleteCascadeCards");
            Deck deck = DeckMother.createWithUser(user, deckName);

            // Save the deck (must capture return value because @Version makes isNew()=false → merge())
            deck = deckRepository.save(deck);

            // Create and save cards referencing the deck
            Card card1 = CardMother.createNew(deck, "Pregunta 1", "Respuesta 1");
            Card card2 = CardMother.createNew(deck, "Pregunta 2", "Respuesta 2");
            cardRepository.saveAllAndFlush(List.of(card1, card2));

            // Force sync and clear context to ensure we test real DB behavior
            entityManager.flush();
            entityManager.clear();

            Long deckId = deck.getId();

            // When: Reload and delete the deck
            Deck managedDeck = deckRepository.findById(deckId).orElseThrow();
            deckRepository.delete(managedDeck);
            deckRepository.flush();

            // Then: Cards must have been cascade-deleted
            assertThat(cardRepository.count()).isZero();
            assertThat(deckRepository.findById(deckId)).isEmpty();
        }
    }

    @Nested
    @DisplayName("Query and finder methods")
    class QueryAndFinderTests {

        @Test
        @DisplayName("SHOULD return card with deck and user WHEN calling findWithDeckAndUser()")
        void shouldReturnCardWithDeckAndUserWhenCallingFindWithDeckAndUser() {
            // Arrange
            Card card = cardRepository.saveAndFlush(
                CardMother.createNew(testDeck, "Fetch Front", "Fetch Back")
            );

            // Act
            Optional<Card> result = cardRepository.findWithDeckAndUser(card.getId(), testUser.getId());

            // Assert
            assertThat(result).isPresent();
            assertThat(result.get().getDeck()).isNotNull();
            assertThat(result.get().getDeck().getUser()).isNotNull();
            assertThat(result.get().getDeck().getId()).isEqualTo(testDeck.getId());
            assertThat(result.get().getDeck().getUser().getId()).isEqualTo(testUser.getId());
        }

        @Test
        @DisplayName("SHOULD return all cards of deck WHEN calling findAllByDeckId()")
        void shouldReturnAllCardsOfDeckWhenCallingFindAllByDeckId() {
            // Arrange
            List<Card> cardsToSave = List.of(
                CardMother.createNew(testDeck, "F1", "B1"),
                CardMother.createNew(testDeck, "F2", "B2"),
                CardMother.createNew(testDeck, "F3", "B3")
            );
            cardRepository.saveAllAndFlush(cardsToSave);

            // Act
            List<Card> foundCards = cardRepository.findAllByDeckId(testDeck.getId());

            // Assert
            assertThat(foundCards).hasSize(3)
                .extracting(Card::getFront)
                .containsExactlyInAnyOrder("F1", "F2", "F3");
        }

        @Test
        @DisplayName("SHOULD return card of user WHEN card belongs to user")
        void shouldReturnCardOfUserWhenCardBelongsToUser() {
            // Arrange
            Card card = cardRepository.saveAndFlush(
                CardMother.createNew(testDeck, "Owner Front", "Owner Back")
            );

            // Act
            Optional<Card> result = cardRepository.findByIdAndDeck_User_Id(card.getId(), testUser.getId());

            // Assert
            assertThat(result).isPresent();
            assertThat(result.get().getId()).isEqualTo(card.getId());
        }

        @Test
        @DisplayName("SHOULD exist WHEN searched by existing front and deck ID")
        void shouldExistWhenSearchingByExistingFrontAndDeckId() {
            // Arrange
            cardRepository.saveAndFlush(
                CardMother.createNew(testDeck, "Exact Front Match", "Back")
            );

            // Act
            boolean exists = cardRepository.existsByFrontIgnoreCaseAndDeckId("Exact Front Match", testDeck.getId());

            // Assert
            assertThat(exists).isTrue();
        }

        @Test
        @DisplayName("SHOULD exist WHEN searched by existing front and deck ID (different case)")
        void shouldExistWhenSearchingByExistingFrontAndDeckIdDifferentCase() {
            // Arrange
            cardRepository.saveAndFlush(
                CardMother.createNew(testDeck, "MiXeD cAsE fRoNt", "Back")
            );

            // Act
            boolean exists = cardRepository.existsByFrontIgnoreCaseAndDeckId("mixed case front", testDeck.getId());

            // Assert
            assertThat(exists).isTrue();
        }

        @Test
        @DisplayName("SHOULD exist WHEN searched by ID and user ID")
        void shouldExistWhenSearchingByIdAndUserId() {
            // Arrange
            Card card = cardRepository.saveAndFlush(
                CardMother.createNew(testDeck, "ID Check Front", "Back")
            );

            // Act
            boolean exists = cardRepository.existsByIdAndDeck_User_Id(card.getId(), testUser.getId());

            // Assert
            assertThat(exists).isTrue();
        }

        @Test
        @DisplayName("SHOULD exist overdue card WHEN searching within deck")
        void shouldExistOverdueCardWhenSearchingWithinDeck() {
            // Arrange
            Card dueCard = CardMother.createNew(testDeck, "Due Card Front", "Due Card Back");
            dueCard.setNextReviewDate(Instant.now().minus(1, ChronoUnit.DAYS));
            cardRepository.saveAndFlush(dueCard);

            // Act
            boolean exists = cardRepository.existsByDeckIdAndNextReviewDateBefore(testDeck.getId(), Instant.now());

            // Assert
            assertThat(exists).isTrue();
        }

        @Test
        @DisplayName("SHOULD return false WHEN searching by non-existing front and deck ID")
        void shouldReturnFalseWhenSearchingByNonExistingFrontAndDeckId() {
            boolean exists = cardRepository.existsByFrontIgnoreCaseAndDeckId("NonExistent", testDeck.getId());

            assertThat(exists).isFalse();
        }

        @Test
        @DisplayName("SHOULD return false WHEN card ID does not belong to user")
        void shouldReturnFalseWhenCardIdDoesNotBelongToUser() {
            Card card = cardRepository.saveAndFlush(
                CardMother.createNew(testDeck, "Other User Card", "Back")
            );
            User otherUser = userRepository.save(
                UserMother.createMinimal(
                    UserMother.uniqueUsername("other", System.nanoTime()),
                    UserMother.uniqueEmail("other", System.nanoTime())
                )
            );

            boolean exists = cardRepository.existsByIdAndDeck_User_Id(card.getId(), otherUser.getId());

            assertThat(exists).isFalse();
        }
    }

    @Nested
    @DisplayName("Pagination and Scrolling: Keyset Windowing")
    class PaginationAndScrolling {

        @Test
        @DisplayName("should Scroll Through Pages Correctly Using PositionAt")
        void shouldScrollThroughPagesCorrectlyUsingPositionAt() {
            Instant sameDate = Instant.parse("2026-01-01T10:00:00Z");

            // Creamos 5 cartas con la misma fecha para probar el desempate por ID
            for (int i = 1; i <= 5; i++) {
                Card c = CardMother.createNew(testDeck, "Front " + i, "Back " + i);
                c.setNextReviewDate(sameDate);
                cardRepository.save(c);
            }
            entityManager.flush();

            CursorPaginationRequest request = CursorPaginationRequest.forCards(null, null, 2, Sort.Direction.ASC);
            Specification<Card> spec = CardSpecifications.hasDeck(testDeck.getId());

            // Página 1
            Window<Card> firstWindow = cardRepository.findBy(spec, q -> q
                .limit(request.pageSize())
                .sortBy(request.toSort())
                .scroll(request.toScrollPosition()));

            // Página 2 usando positionAt del último elemento
            ScrollPosition lastPos = firstWindow.positionAt(firstWindow.getContent().size() - 1);
            Window<Card> secondWindow = cardRepository.findBy(spec, q -> q
                .limit(request.pageSize())
                .sortBy(request.toSort())
                .scroll(lastPos));

            assertSoftly(softly -> {
                softly.assertThat(firstWindow.getContent()).hasSize(2);
                softly.assertThat(secondWindow.getContent()).hasSize(2);
                softly.assertThat(secondWindow.getContent().getFirst().getFront()).isEqualTo("Front 3");
            });
        }

        @Test
        @DisplayName("should Filter Only Pending Cards When Using Pending Specification")
        void shouldFilterOnlyPendingCardsWhenUsingPendingSpecification() {
            Instant now = Instant.now();

            Card pending = CardMother.createNew(testDeck, "Pending", "B");
            pending.setNextReviewDate(now.minusSeconds(10));

            Card future = CardMother.createNew(testDeck, "Future", "B");
            future.setNextReviewDate(now.plusSeconds(10));

            cardRepository.saveAll(List.of(pending, future));
            entityManager.flush();

            CursorPaginationRequest request = CursorPaginationRequest.forCards(null, null, 10, Sort.Direction.ASC);
            Specification<Card> spec = CardSpecifications.hasDeckAndIsPending(testDeck.getId(), now);

            Window<Card> window = cardRepository.findBy(spec, q -> q
                .limit(request.pageSize())
                .sortBy(request.toSort())
                .scroll(request.toScrollPosition()));

            assertThat(window.getContent()).hasSize(1).extracting(Card::getFront).containsExactly("Pending");
        }
    }
}
