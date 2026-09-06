package com.cards.api.integration.data;

import com.cards.api.config.AuditConfig;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import({TestcontainersConfig.class, AuditConfig.class, JpaTestConfig.class})
@DisplayName("Card @Version optimistic locking")
class CardVersionDataIntegrationTest {

    @Autowired
    private CardRepository cardRepository;

    @Autowired
    private DeckRepository deckRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Card savedCard;

    @BeforeEach
    void setUp() {
        User user = userRepository.save(
            UserMother.createMinimal(
                UserMother.uniqueUsername("versioncard", System.currentTimeMillis()),
                UserMother.uniqueEmail("versioncard", System.currentTimeMillis())
            )
        );
        Deck deck = deckRepository.save(
            DeckMother.createWithUser(user, DeckMother.uniqueDeckName("version-deck"))
        );
        savedCard = cardRepository.saveAndFlush(
            CardMother.createNew(deck, "version-front", "version-back")
        );
        entityManager.clear();
    }

    @Test
    @DisplayName("should have version = 0 after initial persist")
    void versionShouldBeZeroAfterPersist() {
        Card reloaded = cardRepository.findById(savedCard.getId()).orElseThrow();
        assertThat(reloaded.getVersion()).isZero();
    }

    @Test
    @DisplayName("should increment version after update")
    void versionShouldIncrementAfterUpdate() {
        Card card = cardRepository.findById(savedCard.getId()).orElseThrow();
        card.setFront("updated-front");
        cardRepository.saveAndFlush(card);

        Card reloaded = cardRepository.findById(savedCard.getId()).orElseThrow();
        assertThat(reloaded.getVersion()).isOne();
    }
}
