package com.cards.api.unit;

import com.cards.api.config.properties.ApplicationProperties;
import com.cards.api.dto.DataForNotificationDTO;
import com.cards.api.repo.UserRepository;
import com.cards.api.service.DeckService;
import com.cards.api.service.notification.EmailService;
import com.cards.api.service.notification.NotificationScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationScheduler")
class NotificationSchedulerTest {

    @Mock
    private UserRepository userRepo;
    @Mock
    private EmailService emailService;
    @Mock
    private DeckService deckService;

    private NotificationScheduler scheduler;
    private Clock fixedClock;

    private static final ZoneId ZONE_UTC = ZoneId.of("UTC");
    private static final Instant MORNING_UTC = Instant.parse("2026-05-16T09:00:00Z");

    @BeforeEach
    void setUp() {
        fixedClock = Clock.fixed(MORNING_UTC, ZONE_UTC);
        var properties = new ApplicationProperties();
        properties.getNotifications().setSendAtHour(9);
        properties.getNotifications().setThresholdHours(20);
        scheduler = new NotificationScheduler(userRepo, emailService, deckService, fixedClock, properties);
    }

    @Nested
    @DisplayName("scheduleDailyReminders")
    class ScheduleDailyReminders {

        @Test
        @DisplayName("should process users in matching zone")
        void shouldProcessUsersInMatchingZone() {
            when(userRepo.findDistinctActiveZoneInfos())
                    .thenReturn(List.of("UTC"));
            DataForNotificationDTO dto = new DataForNotificationDTO(1L, "user1", "user1@test.com", "UTC");
            when(userRepo.findUsersToNotify(any(), eq("UTC"), any()))
                    .thenReturn(List.of(dto));

            scheduler.scheduleDailyReminders();

            verify(emailService).sendReviewReminder("user1@test.com", "user1", 1L, MORNING_UTC);
            verify(deckService).updatePendingFlagsForUsers(List.of(1L), MORNING_UTC);
        }

        @Test
        @DisplayName("should NOT stop processing other users when one fails")
        void shouldContinueAfterUserFailure() {
            when(userRepo.findDistinctActiveZoneInfos())
                    .thenReturn(List.of("UTC"));
            DataForNotificationDTO user1 = new DataForNotificationDTO(1L, "user1", "u1@test.com", "UTC");
            DataForNotificationDTO user2 = new DataForNotificationDTO(2L, "user2", "u2@test.com", "UTC");
            when(userRepo.findUsersToNotify(any(), eq("UTC"), any()))
                    .thenReturn(List.of(user1, user2));

            doThrow(new RuntimeException("Async failure"))
                    .when(emailService).sendReviewReminder(eq("u1@test.com"), eq("user1"), eq(1L), any());

            scheduler.scheduleDailyReminders();

            verify(emailService).sendReviewReminder("u1@test.com", "user1", 1L, MORNING_UTC);
            verify(emailService).sendReviewReminder("u2@test.com", "user2", 2L, MORNING_UTC);
            verify(deckService).updatePendingFlagsForUsers(List.of(1L, 2L), MORNING_UTC);
        }

        @Test
        @DisplayName("should skip zones with invalid timezone IDs")
        void shouldSkipInvalidZone() {
            when(userRepo.findDistinctActiveZoneInfos())
                    .thenReturn(List.of("Invalid/Zone", "UTC"));

            scheduler.scheduleDailyReminders();

            verify(userRepo, never()).findUsersToNotify(any(), eq("Invalid/Zone"), any());
            verify(userRepo).findUsersToNotify(any(), eq("UTC"), any());
        }

        @Test
        @DisplayName("should do nothing when no active zones")
        void shouldDoNothingWhenNoZones() {
            when(userRepo.findDistinctActiveZoneInfos())
                    .thenReturn(List.of());

            scheduler.scheduleDailyReminders();

            verify(userRepo, never()).findUsersToNotify(any(), anyString(), any());
            verifyNoInteractions(emailService);
        }

        @Test
        @DisplayName("should skip zone with no users to notify")
        void shouldSkipZoneWithNoUsers() {
            when(userRepo.findDistinctActiveZoneInfos()).thenReturn(List.of("UTC"));
            when(userRepo.findUsersToNotify(any(), eq("UTC"), any()))
                    .thenReturn(List.of());

            scheduler.scheduleDailyReminders();

            verify(emailService, never()).sendReviewReminder(any(), any(), any(), any());
            verify(deckService, never()).updatePendingFlagsForUsers(any(), any());
        }
    }
}
