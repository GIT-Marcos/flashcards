package com.cards.api.listener;

import com.cards.api.dto.event.UserLoginEvent;
import com.cards.api.repo.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Clock;

/**
 * Updates the user's lastLogin timestamp.
 */
@Component
public class UserLoginEventListener {

    private static final Logger log = LoggerFactory.getLogger(UserLoginEventListener.class);
    private final Clock clock;
    private final UserRepository userRepo;

    public UserLoginEventListener(Clock clock, UserRepository userRepo) {
        this.clock = clock;
        this.userRepo = userRepo;
    }

    @Async("updateLastLoginExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleLastLoginUpdate(UserLoginEvent event) {
        int updatedRows = userRepo.updateLastLogin(event.userId(), clock.instant());
        if (updatedRows == 0) {
            log.warn("Could not update lastLogin. User {} may have been deleted.", event.userId());
        }
    }
}
