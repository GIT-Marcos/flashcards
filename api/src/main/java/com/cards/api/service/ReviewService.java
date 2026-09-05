package com.cards.api.service;

import com.cards.api.dto.request.ReviewRequest;
import com.cards.api.dto.response.CardResponse;
import com.cards.api.entity.*;
import com.cards.api.exception.ResourceNotFoundException;
import com.cards.api.exception.domain.InvalidReviewDateException;
import com.cards.api.mapper.CardMapper;
import com.cards.api.repo.CardRepository;
import com.cards.api.repo.CardReviewLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Service
public class ReviewService {

    private final SessionService sessionService;
    private final CardReviewLogRepository cardReviewLogRepo;
    private final CardRepository cardRepo;
    private final CardMapper cardMapper;
    private final Clock clock;

    public ReviewService(SessionService sessionService, CardReviewLogRepository cardReviewLogRepo, CardRepository cardRepo, CardMapper cardMapper, Clock clock) {
        this.sessionService = sessionService;
        this.cardReviewLogRepo = cardReviewLogRepo;
        this.cardRepo = cardRepo;
        this.cardMapper = cardMapper;
        this.clock = clock;
    }

    //todo late: parece que este método hace mucho
    @Transactional
    public CardResponse review(Long cardId, Long authUserId, ReviewRequest request) {
        Card cardToReview = cardRepo.findWithDeckAndUser(cardId, authUserId).orElseThrow(
                () -> new ResourceNotFoundException("Card not found")
        );

        Instant now = Instant.now(clock);

        if (cardToReview.getNextReviewDate().isAfter(now)) {
            throw new InvalidReviewDateException();
        }
        User userReview = cardToReview.getDeck().getUser();
        Deck deck = cardToReview.getDeck();

        StudySession studySession = sessionService.getOrCreateActiveSession(authUserId);

        cardToReview.applyReview(request.quality(), userReview, now);

        updateDeckPendingStatusIfEmpty(deck, now);

        CardReviewLog log = CardReviewLog.builder()
                .quality(request.quality())
                .card(cardToReview)
                .user(userReview)
                .studySession(studySession)
                .build();
        cardReviewLogRepo.save(log);

        sessionService.updateMetrics(studySession.getId(), request.quality(), now);

        return cardMapper.toResponse(cardToReview);
    }

    private void updateDeckPendingStatusIfEmpty(Deck deck, Instant now) {
        boolean hasMorePendingCards = cardRepo.existsByDeckIdAndNextReviewDateBefore(deck.getId(), now);

        if (!hasMorePendingCards)
            deck.setHasPendingCards(false);
    }
}
