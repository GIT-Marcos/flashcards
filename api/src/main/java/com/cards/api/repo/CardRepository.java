package com.cards.api.repo;

import com.cards.api.entity.Card;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface CardRepository extends JpaRepository<Card, Long>, JpaSpecificationExecutor<Card> {

    @Query("SELECT c FROM Card c JOIN FETCH c.deck d JOIN FETCH d.user WHERE c.id = :cardId AND d.user.id = :userId")
    Optional<Card> findWithDeckAndUser(@Param("cardId") Long cardId, @Param("userId") Long userId);

    List<Card> findAllByDeckId(Long deckId);

    Optional<Card> findByIdAndDeck_User_Id(Long cardId, Long userId);

    boolean existsByFrontIgnoreCaseAndDeckId(String front, Long deckId);

    boolean existsByIdAndDeck_User_Id(Long cardId, Long userId);

    boolean existsByDeckIdAndNextReviewDateBefore(Long deckId, Instant now);
}
