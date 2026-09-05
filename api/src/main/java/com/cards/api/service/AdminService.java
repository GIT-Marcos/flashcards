package com.cards.api.service;

import com.cards.api.dto.request.CursorPaginationRequest;
import com.cards.api.dto.response.CardResponse;
import com.cards.api.dto.response.DeckResponse;
import com.cards.api.dto.response.UserResponse;
import com.cards.api.entity.Card;
import com.cards.api.entity.Deck;
import com.cards.api.entity.User;
import com.cards.api.exception.ResourceNotFoundException;
import com.cards.api.mapper.CardMapper;
import com.cards.api.mapper.DeckMapper;
import com.cards.api.mapper.UserMapper;
import com.cards.api.repo.CardRepository;
import com.cards.api.repo.DeckRepository;
import com.cards.api.repo.UserRepository;
import com.cards.api.service.notification.EmailService;
import com.cards.api.specification.CardSpecifications;
import com.cards.api.specification.DeckSpecification;
import org.springframework.data.domain.Window;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Service
public class AdminService {

    private final DeckRepository deckRepo;
    private final UserRepository userRepo;
    private final CardRepository cardRepo;
    private final EmailService emailService;

    private final UserMapper userMapper;
    private final DeckMapper deckMapper;
    private final CardMapper cardMapper;
    private final Clock clock;

    public AdminService(DeckRepository deckRepo, UserRepository userRepo, CardRepository cardRepo, EmailService emailService, UserMapper userMapper, DeckMapper deckMapper, CardMapper cardMapper, Clock clock) {
        this.deckRepo = deckRepo;
        this.userRepo = userRepo;
        this.cardRepo = cardRepo;
        this.emailService = emailService;
        this.userMapper = userMapper;
        this.deckMapper = deckMapper;
        this.cardMapper = cardMapper;
        this.clock = clock;
    }

    public Window<UserResponse> getAllUsers(CursorPaginationRequest req) {
        CursorPaginationRequest pagination = CursorPaginationRequest.forUsers(
            req.lastId(), req.cursorValue(), req.pageSize(), req.direction());
        Specification<User> spec = (root, query, cb) -> cb.conjunction();
        return userRepo.findBy(spec, query -> query
            .limit(pagination.pageSize())
            .sortBy(pagination.toSort())
            .scroll(pagination.toScrollPosition())
        ).map(userMapper::toResponse);
    }

    public Window<DeckResponse> getUserDecks(Long userId, CursorPaginationRequest req) {
        CursorPaginationRequest pagination = CursorPaginationRequest.forDecks(
            req.lastId(), req.cursorValue(), req.pageSize(), req.direction());
        var spec = DeckSpecification.getFromUser(userId);
        return deckRepo.findBy(spec, query -> query
            .limit(pagination.pageSize())
            .sortBy(pagination.toSort())
            .scroll(pagination.toScrollPosition())
        ).map(deckMapper::toResponse);
    }

    public Window<CardResponse> getDeckCards(Long deckId, CursorPaginationRequest req) {
        CursorPaginationRequest pagination = CursorPaginationRequest.forDecks(
            req.lastId(), req.cursorValue(), req.pageSize(), req.direction());
        var spec = CardSpecifications.hasDeck(deckId);
        return cardRepo.findBy(spec, query -> query
            .limit(pagination.pageSize())
            .sortBy(pagination.toSort())
            .scroll(pagination.toScrollPosition())
        ).map(cardMapper::toResponse);
    }

    public CardResponse getCard(Long cardId) {
        return cardRepo.findById(cardId)
            .map(cardMapper::toResponse)
            .orElseThrow(() -> new ResourceNotFoundException("The card with the id '" + cardId + "' does not exist"));
    }

    @Transactional
    public void deleteUser(Long userId) {
        if (!userRepo.existsById(userId))
            throw new ResourceNotFoundException("The user with the id '" + userId + "' does not exist");

        userRepo.deleteById(userId);
    }

    @Transactional
    public void deleteDeck(Long deckId) {
        if (!deckRepo.existsById(deckId))
            throw new ResourceNotFoundException("The deck with the id '" + deckId + "' does not exist");

        deckRepo.deleteById(deckId);
    }

    @Transactional
    public void deleteCard(Long cardId) {
        Card card = cardRepo.findById(cardId)
            .orElseThrow(() -> new ResourceNotFoundException("The card with the id '" + cardId + "' does not exist"));

        Deck deck = card.getDeck();
        cardRepo.delete(card);

        if (!cardRepo.existsByDeckIdAndNextReviewDateBefore(deck.getId(), Instant.now(clock))) {
            deck.setHasPendingCards(false);
        }
    }

    /**
     * Sends a review reminder notification to the specified user immediately.
     * <p>
     * Unlike the scheduler's batch flow (which is asynchronous), this method
     * blocks until the email is sent or all retries are exhausted. The caller
     * receives synchronous feedback about the outcome.
     *
     * @param userId the ID of the user to notify
     * @throws ResourceNotFoundException if no user exists with the given ID
     * @throws RuntimeException          if the email could not be sent after all retries
     */
    public void sendNotification(Long userId) {
        Instant now = Instant.now(clock);
        User user = userRepo.findById(userId).orElseThrow(
            () -> new ResourceNotFoundException("User not found")
        );

        emailService.sendReviewReminderSync(user.getEmail(), user.getUsername(), user.getId(), now);
    }
}
