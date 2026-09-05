package com.cards.api.specification;

import com.cards.api.entity.Card;
import com.cards.api.entity.Deck;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;

public class DeckSpecification {

    private DeckSpecification() {
    }

    public static Specification<Deck> getFromUser(Long userId) {
        return (root, query, cb) ->
                cb.equal(root.get("user").get("id"), userId);
    }

    public static Specification<Deck> getFromUserAndPendingCards(Long userId, Instant now) {
        return (root, query, cb) -> {
            Subquery<Long> subquery = query.subquery(Long.class);
            Root<Card> cardRoot = subquery.from(Card.class);
            subquery.select(cb.literal(1L));
            subquery.where(
                    cb.equal(cardRoot.get("deck").get("id"), root.get("id")),
                    cb.lessThanOrEqualTo(cardRoot.get("nextReviewDate"), now)
            );
            return cb.and(
                    cb.equal(root.get("user").get("id"), userId),
                    cb.exists(subquery)
            );
        };
    }
}
