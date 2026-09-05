package com.cards.api.mapper;

import com.cards.api.dto.request.CreateCardRequest;
import com.cards.api.dto.request.PatchCardRequest;
import com.cards.api.dto.response.CardResponse;
import com.cards.api.entity.Card;
import com.cards.api.entity.Deck;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
public class CardMapper {

    public Card toEntity(Deck deck, CreateCardRequest dto, Instant now) {
        if (dto == null || deck == null) return null;

        return Card.builder()
            .front(dto.front())
            .back(dto.back())
            .deck(deck)
            .nextReviewDate(now)
            .build();
    }

    public Card patchEntity(Card toPatch, PatchCardRequest dto) {
        if (dto == null || toPatch == null) return null;

        if (dto.front() != null)
            toPatch.setFront(dto.front());

        if (dto.back() != null)
            toPatch.setBack(dto.back());

        return toPatch;
    }

    public CardResponse toResponse(Card card) {
        if (card == null) return null;

        return new CardResponse(
            card.getId(),
            card.getFront(),
            card.getBack(),
            card.getNextReviewDate()
        );
    }

    public List<CardResponse> toResponse(List<Card> cards) {
        if (cards == null) return List.of();

        return cards.stream().map(this::toResponse).toList();
    }
}
