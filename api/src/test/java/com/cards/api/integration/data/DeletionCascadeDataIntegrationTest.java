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

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Congela la política de borrado descrita en el SDD §6.5 "Borrado de Datos":
 * <ul>
 *     <li>Borrar un User destruye decks, cards, sesiones y logs.</li>
 *     <li>Borrar un Deck o una Card preserva logs y sesiones, nullificando card_id.</li>
 * </ul>
 * El cascado lo ejecutan las acciones referenciales de Flyway (V1), no JPA.
 */
@DataJpaTest
@Import({TestcontainersConfig.class, AuditConfig.class, JpaTestConfig.class})
@DisplayName("Deletion cascade")
class DeletionCascadeDataIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DeckRepository deckRepository;

    @Autowired
    private CardRepository cardRepository;

    @Autowired
    private StudySessionRepository sessionRepository;

    @Autowired
    private CardReviewLogRepository logRepository;

    @Autowired
    private TestEntityManager entityManager;

    @BeforeEach
    void setUp() {
        userRepository.deleteAllInBatch();
    }

    /**
     * Escenario común: user -&gt; deck -&gt; card, más una sesión con un log que apunta a la card.
     */
    private Fixture arrange() {
        User user = userRepository.save(
                UserMother.createMinimal(
                        UserMother.uniqueUsername("owner", System.nanoTime()),
                        UserMother.uniqueEmail("owner", System.nanoTime())
                )
        );
        Deck deck = deckRepository.save(
                DeckMother.createWithUser(user, DeckMother.uniqueDeckName("Deck"))
        );
        Card card = cardRepository.save(
                CardMother.createDueCard(deck, "¿Capital de Francia?", "París", Instant.now())
        );
        StudySession session = sessionRepository.save(StudySessionMother.createForUser(user));
        CardReviewLog log = logRepository.save(ReviewLogMother.create(user, card, session, 4));

        entityManager.flush();
        // El log queda gestionado en el contexto apuntando a la Card; al hacer em.remove(card)
        // Hibernate lanzaria TransientPropertyValueException en el flush (una entidad gestionada
        // no puede referenciar una instancia eliminada). Limpiando el contexto se replica el
        // escenario real: en produccion CardService/AdminService cargan solo la Card y la
        // coleccion de logs es LAZY, nunca se inicializa.
        entityManager.clear();

        return new Fixture(user, deck, card, session, log);
    }

    private record Fixture(User user, Deck deck, Card card, StudySession session, CardReviewLog log) {
    }

    // ========================================================================
    // DELETE user
    // ========================================================================

    @Nested
    @DisplayName("DELETE user")
    class DeleteUser {

        @Test
        @DisplayName("SHOULD delete decks, cards, sessions and logs WHEN user is deleted")
        void shouldDeleteEverything_whenUserIsDeleted() {
            // Arrange
            Fixture fixture = arrange();

            // Act
            userRepository.bulkDeleteById(fixture.user().getId());
            entityManager.clear();

            // Assert
            assertThat(entityManager.find(User.class, fixture.user().getId())).isNull();
            assertThat(entityManager.find(Deck.class, fixture.deck().getId())).isNull();
            assertThat(entityManager.find(Card.class, fixture.card().getId())).isNull();
            assertThat(entityManager.find(StudySession.class, fixture.session().getId())).isNull();
            assertThat(entityManager.find(CardReviewLog.class, fixture.log().getId())).isNull();
        }
    }

    // ========================================================================
    // DELETE deck
    // ========================================================================

    @Nested
    @DisplayName("DELETE deck")
    class DeleteDeck {

        @Test
        @DisplayName("SHOULD delete cards and preserve logs and sessions WHEN deck is deleted")
        void shouldPreserveHistory_whenDeckIsDeleted() {
            // Arrange
            Fixture fixture = arrange();

            // Act
            deckRepository.bulkDeleteById(fixture.deck().getId());
            entityManager.clear();

            // Assert
            assertThat(entityManager.find(Deck.class, fixture.deck().getId())).isNull();
            assertThat(entityManager.find(Card.class, fixture.card().getId())).isNull();

            CardReviewLog log = entityManager.find(CardReviewLog.class, fixture.log().getId());
            assertThat(log).isNotNull();
            assertThat(log.getCard()).isNull();

            assertThat(entityManager.find(StudySession.class, fixture.session().getId())).isNotNull();
        }
    }

    // ========================================================================
    // DELETE card
    // ========================================================================

    @Nested
    @DisplayName("DELETE card")
    class DeleteCard {

        @Test
        @DisplayName("SHOULD preserve log with null card and keep session WHEN card is deleted")
        void shouldPreserveHistory_whenCardIsDeleted() {
            // Arrange
            Fixture fixture = arrange();

            // Act
            cardRepository.delete(fixture.card());
            entityManager.flush();
            entityManager.clear();

            // Assert
            assertThat(entityManager.find(Card.class, fixture.card().getId())).isNull();

            CardReviewLog log = entityManager.find(CardReviewLog.class, fixture.log().getId());
            assertThat(log).isNotNull();
            assertThat(log.getCard()).isNull();

            assertThat(entityManager.find(StudySession.class, fixture.session().getId())).isNotNull();
        }
    }
}
