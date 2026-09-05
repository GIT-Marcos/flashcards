package com.cards.api.service;

import com.cards.api.dto.request.CreateCardRequest;
import com.cards.api.dto.request.CursorPaginationRequest;
import com.cards.api.dto.request.PatchCardRequest;
import com.cards.api.dto.response.CardResponse;
import com.cards.api.entity.Card;
import com.cards.api.entity.Deck;
import com.cards.api.exception.ResourceNotFoundException;
import com.cards.api.exception.domain.DuplicatedCardException;
import com.cards.api.mapper.CardMapper;
import com.cards.api.repo.CardRepository;
import com.cards.api.repo.DeckRepository;
import com.cards.api.specification.CardSpecifications;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Window;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Service
public class CardService {

    private final CardRepository cardRepo;
    private final DeckRepository deckRepo;
    private final CardMapper cardMapper;
    private final Clock clock;

    public CardService(CardRepository cardRepo, DeckRepository deckRepo, CardMapper cardMapper, Clock clock) {
        this.cardRepo = cardRepo;
        this.deckRepo = deckRepo;
        this.cardMapper = cardMapper;
        this.clock = clock;
    }

    @Transactional
    public CardResponse create(Long deckId, Long authUserId, CreateCardRequest request) {
        Deck managedDeck = deckRepo.findByIdAndUserId(deckId, authUserId)
            .orElseThrow(() -> new ResourceNotFoundException("That deck does not exist."));

        if (cardRepo.existsByFrontIgnoreCaseAndDeckId(request.front(), deckId))
            throw new DuplicatedCardException(request.front());

        try {
            Card toPersist = cardMapper.toEntity(managedDeck, request, Instant.now(clock));
            toPersist = cardRepo.save(toPersist);
            managedDeck.setHasPendingCards(true);
            return cardMapper.toResponse(toPersist);
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicatedCardException(request.front());
        }
    }

    @Transactional
    public CardResponse patch(Long cardId, Long authUserId, PatchCardRequest request) {
        Card toPatch = cardRepo.findByIdAndDeck_User_Id(cardId, authUserId)
            .orElseThrow(() -> new ResourceNotFoundException("Card not found"));

        if (request.front() == null && request.back() == null) {
            return cardMapper.toResponse(toPatch);
        }

        if (request.front() != null && !request.front().equals(toPatch.getFront())) {
            if (cardRepo.existsByFrontIgnoreCaseAndDeckId(request.front(), toPatch.getDeck().getId())) {
                throw new DuplicatedCardException(request.front());
            }
        }

        try {
            toPatch = cardMapper.patchEntity(toPatch, request);
            toPatch = cardRepo.save(toPatch);
            return cardMapper.toResponse(toPatch);
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicatedCardException(request.front());
        }
    }

    @Transactional
    public void delete(Long cardId, Long userId) {
        Card toDelete = cardRepo.findByIdAndDeck_User_Id(cardId, userId)
            .orElseThrow(() -> new ResourceNotFoundException("Card not found"));

        Deck deck = toDelete.getDeck();
        cardRepo.delete(toDelete);

        if (!cardRepo.existsByDeckIdAndNextReviewDateBefore(deck.getId(), Instant.now(clock))) {
            deck.setHasPendingCards(false);
        }
    }

    /** LECTURA */

    public CardResponse getById(Long cardId, Long authUserId) {
        return cardRepo.findByIdAndDeck_User_Id(cardId, authUserId)
            .map(cardMapper::toResponse)
            .orElseThrow(() -> new ResourceNotFoundException("Card not found"));
    }

    public Window<CardResponse> getPendingByDeck(Long deckId, Long authUserId, CursorPaginationRequest req) {
        verifyDeckAndOwner(deckId, authUserId);

        CursorPaginationRequest pagination = CursorPaginationRequest.forCards(
            req.lastId(), req.cursorValue(), req.pageSize(), req.direction());

        Instant now = Instant.now(clock);
        Specification<Card> spec = CardSpecifications.hasDeckAndIsPending(deckId, now);

        Window<Card> cardWindow = cardRepo.findBy(spec, query -> query
            .limit(pagination.pageSize())
            .sortBy(pagination.toSort())
            .scroll(pagination.toScrollPosition())
        );

        return cardWindow.map(cardMapper::toResponse);
    }

    public Window<CardResponse> getByDeck(Long deckId, Long authUserId, CursorPaginationRequest req) {
        verifyDeckAndOwner(deckId, authUserId);

        CursorPaginationRequest pagination = CursorPaginationRequest.forCards(
            req.lastId(), req.cursorValue(), req.pageSize(), req.direction());

        Specification<Card> spec = CardSpecifications.hasDeck(deckId);

        Window<Card> cardWindow = cardRepo.findBy(spec, query -> query
            .limit(pagination.pageSize())
            .sortBy(pagination.toSort())
            .scroll(pagination.toScrollPosition())
        );

        return cardWindow.map(cardMapper::toResponse);
    }

    private void verifyDeckAndOwner(Long deckId, Long authUserId) {
        if (!deckRepo.existsByIdAndUserId(deckId, authUserId))
            throw new ResourceNotFoundException("Deck not found");
    }
}
