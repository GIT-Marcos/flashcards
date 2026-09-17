package com.cards.api.repo;

import com.cards.api.entity.Deck;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface DeckRepository extends JpaRepository<Deck, Long>, JpaSpecificationExecutor<Deck> {

    @Modifying(clearAutomatically = true)
    @Query("""
                UPDATE Deck d SET d.hasPendingCards = true
                WHERE d.user.id IN :userIds
                  AND EXISTS (SELECT 1 FROM Card c WHERE c.deck.id = d.id AND c.nextReviewDate <= :now)
            """)
    int bulkUpdateHasPendingCardsForUsers(@Param("userIds") List<Long> userIds, @Param("now") Instant now);
    // Usuarios con tarjetas pendientes

    /**
     * Borrado físico en una única sentencia. El cascado a cards lo ejecuta la BD
     * (ON DELETE CASCADE); la nullificación de card_review_log.card_id, el ON DELETE SET NULL.
     * Ver SDD §6.5 "Borrado de Datos".
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM Deck d WHERE d.id = :id")
    int bulkDeleteById(@Param("id") Long id);

    List<Deck> findAllByUserId(Long userId);

    Optional<Deck> findByIdAndUserId(Long deckId, Long userId);

    Boolean existsByUserIdAndNameIgnoreCase(Long userId, String deckName);

    boolean existsByIdAndUserId(Long deckId, Long userId);
}
