package com.cards.api.listener;

import com.cards.api.dto.event.UserTimeZoneUpdateEvent;
import com.cards.api.repo.UserRepository;
import com.cards.api.util.TimeZoneUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Updates the user's timezone.
 */
@Component
public class UserSettingsEventListener {

    private static final Logger log = LoggerFactory.getLogger(UserSettingsEventListener.class);
    private final UserRepository userRepo;

    public UserSettingsEventListener(UserRepository userRepo) {
        this.userRepo = userRepo;
    }

    /**
     * Listens for {@link UserTimeZoneUpdateEvent} to update the user's
     * timezone in the database.
     *
     * <p>Uses {@code @EventListener} (not {@code @TransactionalEventListener}) because
     * the event is published by {@code TimeZoneInterceptor} outside any
     * transaction (HTTP context without tx). With no transaction in the publisher,
     * using {@code TransactionPhase.AFTER_COMMIT} would not make sense.
     *
     * <p>The function has its own {@code @Transactional} to ensure the
     * DB write happens within a transaction on the async thread.
     *
     * <p>It is @Async with the "systemEventsExecutor" to avoid blocking the HTTP thread.
     */
    @Async("systemEventsExecutor")
    @EventListener
    @Transactional
    public void handleTimeZoneUpdate(UserTimeZoneUpdateEvent event) {
        if (!TimeZoneUtils.isValid(event.zoneInfo())) {
            log.warn("Invalid timezone received in event for {}: '{}'", event.username(), event.zoneInfo());
            return;
        }

        userRepo.findByUsernameIgnoreCase(event.username()).ifPresentOrElse(
            user -> {
                if (!event.zoneInfo().equals(user.getZoneInfo())) {
                    user.setZoneInfo(event.zoneInfo());
                    userRepo.save(user);
                }
            },
            () -> log.error("Error while updating zone info")
        );
    }
}
