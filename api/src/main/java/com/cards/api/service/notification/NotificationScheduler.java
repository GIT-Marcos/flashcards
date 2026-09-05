package com.cards.api.service.notification;

import com.cards.api.config.properties.ApplicationProperties;
import com.cards.api.dto.DataForNotificationDTO;
import com.cards.api.repo.UserRepository;
import com.cards.api.service.DeckService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Schedules email sending for review reminders.
 */
@Service
public class NotificationScheduler {

    private static final Logger log = LoggerFactory.getLogger(NotificationScheduler.class);

    private final UserRepository userRepo;
    private final EmailService emailService;
    private final DeckService deckService;
    private final Clock clock;
    private final ApplicationProperties properties;

    public NotificationScheduler(UserRepository userRepo, EmailService emailService, DeckService deckService, Clock clock,
                                 ApplicationProperties properties) {
        this.userRepo = userRepo;
        this.emailService = emailService;
        this.deckService = deckService;
        this.clock = clock;
        this.properties = properties;
    }

    @Scheduled(cron = "${application.notifications.cron}")
    public void scheduleDailyReminders() {
        var notifications = properties.getNotifications();
        Instant now = Instant.now(clock);
        Instant threshold = now.minus(notifications.getThresholdHours(), ChronoUnit.HOURS);

        // Fetch only zones that have users
        List<String> activeZones = userRepo.findDistinctActiveZoneInfos();
        if (activeZones.isEmpty()) {
            log.debug("No users with configured timezone");
            return;
        }

        int sendAtHour = properties.getNotifications().getSendAtHour();
        List<String> targetZones = activeZones.stream()
            .filter(zone -> isTargetHourInZone(zone, now))
            .toList();
        if (targetZones.isEmpty()) {
            log.debug("No active zone matches hour {}", sendAtHour);
            return;
        }
        log.info("Target zones in this run: {}", targetZones);

        for (String zone : targetZones) {
            processZone(zone, now, threshold);
        }
    }

    private void processZone(String zone, Instant now, Instant threshold) {
        List<DataForNotificationDTO> usersToNotify = userRepo.findUsersToNotify(now, zone, threshold);

        if (usersToNotify.isEmpty()) {
            return;
        }

        log.info("Processing {} users in zone {}", usersToNotify.size(), zone);

        for (DataForNotificationDTO dto : usersToNotify) {
            processSingleUser(dto, now);
        }

        List<Long> userIds = usersToNotify.stream().map(DataForNotificationDTO::id).toList();
        deckService.updatePendingFlagsForUsers(userIds, now);
    }

    private boolean isTargetHourInZone(String zoneId, Instant now) {
        try {
            return now.atZone(ZoneId.of(zoneId))
                .getHour() == properties.getNotifications().getSendAtHour();
        } catch (DateTimeException e) {
            log.warn("Invalid timezone found in DB: '{}'. It will be ignored.", zoneId);
            return false;
        }
    }

    private void processSingleUser(DataForNotificationDTO dto, Instant now) {
        try {
            emailService.sendReviewReminder(dto.email(), dto.username(), dto.id(), now);
        } catch (Exception e) {
            log.warn("Failed to process notification for user {} ({}): {}",
                dto.id(), dto.email(), e.getMessage());
        }
    }
}
