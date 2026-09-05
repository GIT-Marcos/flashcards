package com.cards.api.specification;

import com.cards.api.entity.Card;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;

public class CardSpecifications {

    private CardSpecifications() {
    }

    public static Specification<Card> hasDeck(Long deckId) {
        return (root, query, cb) ->
                cb.equal(root.get("deck").get("id"), deckId);
    }

    public static Specification<Card> hasDeckAndIsPending(Long deckId, Instant referenceTime) {
        return (root, query, cb) -> cb.and(
                cb.equal(root.get("deck").get("id"), deckId),
                cb.lessThanOrEqualTo(root.get("nextReviewDate"), referenceTime)
        );
    }
}
