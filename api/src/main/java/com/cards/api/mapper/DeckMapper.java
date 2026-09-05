package com.cards.api.mapper;

import com.cards.api.dto.request.CreateDeckRequest;
import com.cards.api.dto.request.PatchDeckRequest;
import com.cards.api.dto.response.DeckResponse;
import com.cards.api.entity.Deck;
import com.cards.api.entity.User;
import org.springframework.stereotype.Component;

@Component
public class DeckMapper {

    public Deck toEntity(User owner, CreateDeckRequest request) {
        if (request == null || owner == null) return null;

        return Deck.builder()
            .name(request.name())
            .user(owner)
            .build();
    }

    public Deck patchEntity(Deck toPatch, PatchDeckRequest request) {
        if (toPatch == null || request == null) return null;

        if (request.name() != null)
            toPatch.setName(request.name());

        return toPatch;
    }

    public DeckResponse toResponse(Deck deck) {
        if (deck == null) return null;

        return new DeckResponse(
            deck.getId(),
            deck.getName(),
            deck.getHasPendingCards(),
            deck.getCreatedAt(),
            deck.getUpdatedAt()
        );
    }
}
