package com.cards.api.service;

import com.cards.api.dto.request.CreateDeckRequest;
import com.cards.api.dto.request.CursorPaginationRequest;
import com.cards.api.dto.request.PatchDeckRequest;
import com.cards.api.dto.response.DeckResponse;
import com.cards.api.entity.Deck;
import com.cards.api.entity.User;
import com.cards.api.exception.ResourceNotFoundException;
import com.cards.api.exception.domain.DuplicatedDeckException;
import com.cards.api.mapper.DeckMapper;
import com.cards.api.repo.DeckRepository;
import com.cards.api.repo.UserRepository;
import com.cards.api.specification.DeckSpecification;
import io.micrometer.core.instrument.Counter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Window;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
public class DeckService {

    private final DeckRepository deckRepo;
    private final UserRepository userRepo;
    private final DeckMapper deckMapper;
    private final Counter decksCreatedCounter;
    private final Clock clock;

    public DeckService(DeckRepository deckRepo, UserRepository userRepo, DeckMapper deckMapper, Counter decksCreatedCounter, Clock clock) {
        this.deckRepo = deckRepo;
        this.userRepo = userRepo;
        this.deckMapper = deckMapper;
        this.decksCreatedCounter = decksCreatedCounter;
        this.clock = clock;
    }

    @Transactional
    public DeckResponse create(Long authUserId, CreateDeckRequest request) {
        User owner = userRepo.getReferenceById(authUserId);

        if (deckRepo.existsByUserIdAndNameIgnoreCase(authUserId, request.name()))
            throw new DuplicatedDeckException(request.name());

        try {
            Deck toPersist = deckMapper.toEntity(owner, request);
            toPersist = deckRepo.save(toPersist);
            decksCreatedCounter.increment();
            return deckMapper.toResponse(toPersist);
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicatedDeckException(request.name());
        }
    }

    @Transactional
    public DeckResponse patch(Long deckId, Long authUserId, PatchDeckRequest request) {
        Deck toPatch = deckRepo.findByIdAndUserId(deckId, authUserId)
            .orElseThrow(() -> new ResourceNotFoundException("Deck not found"));

        if (request.name() != null && !request.name().equals(toPatch.getName())) {
            if (deckRepo.existsByUserIdAndNameIgnoreCase(authUserId, request.name())) {
                throw new DuplicatedDeckException(request.name());
            }
        } else {
            return deckMapper.toResponse(toPatch);
        }

        try {
            toPatch = deckMapper.patchEntity(toPatch, request);
            toPatch = deckRepo.save(toPatch);
            return deckMapper.toResponse(toPatch);
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicatedDeckException(request.name());
        }
    }

    @Transactional
    public void delete(Long deckId, Long userId) {
        if (!deckRepo.existsByIdAndUserId(deckId, userId))
            throw new ResourceNotFoundException("Deck not found");

        deckRepo.deleteById(deckId);
    }

    public DeckResponse getDeckById(Long deckId, Long authUserId) {
        return deckRepo.findByIdAndUserId(deckId, authUserId)
            .map(deckMapper::toResponse)
            .orElseThrow(() -> new ResourceNotFoundException("Deck not found"));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updatePendingFlagsForUsers(List<Long> userIds, Instant now) {
        deckRepo.bulkUpdateHasPendingCardsForUsers(userIds, now);
    }

    public Window<DeckResponse> getDecks(Long authUserId, CursorPaginationRequest req) {
        Specification<Deck> spec = DeckSpecification.getFromUser(authUserId);
        CursorPaginationRequest pagination = CursorPaginationRequest.forDecks(
            req.lastId(), req.cursorValue(), req.pageSize(), req.direction());

        Window<Deck> deckWindow = deckRepo.findBy(spec, query -> query
            .limit(pagination.pageSize())
            .sortBy(pagination.toSort())
            .scroll(pagination.toScrollPosition())
        );

        return deckWindow.map(deckMapper::toResponse);
    }

    public Window<DeckResponse> getDueDecks(Long authUserId, CursorPaginationRequest req) {
        Specification<Deck> spec = DeckSpecification.getFromUserAndPendingCards(authUserId, Instant.now(clock));
        CursorPaginationRequest pagination = CursorPaginationRequest.forDecks(
            req.lastId(), req.cursorValue(), req.pageSize(), req.direction());

        Window<Deck> window = deckRepo.findBy(spec, query -> query
            .limit(pagination.pageSize())
            .sortBy(pagination.toSort())
            .scroll(pagination.toScrollPosition())
        );

        return window.map(deckMapper::toResponse);
    }
}
