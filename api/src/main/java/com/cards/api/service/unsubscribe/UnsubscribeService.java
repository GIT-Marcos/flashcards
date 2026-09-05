package com.cards.api.service.unsubscribe;

import com.cards.api.exception.domain.InvalidUnsubscribeTokenException;
import com.cards.api.repo.UserRepository;
import com.cards.api.service.JwtService;
import com.cards.api.util.TokenType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Service
public class UnsubscribeService {

    private final JwtService jwtService;
    private final UserRepository userRepo;
    private final Clock clock;

    public UnsubscribeService(JwtService jwtService, UserRepository userRepo, Clock clock) {
        this.jwtService = jwtService;
        this.userRepo = userRepo;
        this.clock = clock;
    }

    @Transactional
    public void unsubscribe(String token) {
        if (!jwtService.isTokenType(token, TokenType.UNSUBSCRIBE)) {
            throw new InvalidUnsubscribeTokenException("Invalid unsubscribe link");
        }
        Long userId = jwtService.extractUserId(token);
        userRepo.findById(userId).ifPresent(user -> {
            user.setNotificationsEnabled(false);
            user.setLastNotificationSent(Instant.now(clock));
            userRepo.save(user);
        });
    }
}
