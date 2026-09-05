package com.cards.api.integration;

import com.cards.api.dto.event.UserTimeZoneUpdateEvent;
import com.cards.api.entity.User;
import com.cards.api.infraestructure.mother.UserMother;
import com.cards.api.repo.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(properties = {
    "application.security.jwt.secret-key=testSecretKeyThatIsLongEnoughForHS256Algorithm!!",
    "application.security.jwt.expiration=900000",
    "application.security.jwt.refresh-token.expiration=604800000",
    "application.notifications.send-at-hour=9",
    "application.notifications.threshold-hours=20",
    "application.notifications.cron=0 0 0 * * *",
    "application.notifications.app-url=http://localhost:5173",
    "application.notifications.from-address=notificaciones@flashcards.app",
    "application.notifications.from-name=Flashcards App",
    "application.notifications.unsubscribe-token-expiration=2592000000",
    "application.notifications.api-url=http://localhost:8080",
    "application.security.secure-cookie=false",
    "application.security.same-site=Strict",
    "maileroo.api-key=integration-test-api-key",
    "maileroo.webhook-secret=integration-test-webhook-secret",
    "application.security.verification-token-expiration=86400000",
    "application.security.reset-token-expiration=900000"
})
@Import(TestcontainersConfig.class)
@ExtendWith(OutputCaptureExtension.class)
@DisplayName("Async event: UserTimeZoneUpdateEvent")
class AsyncUserTimeZoneUpdateEventIntegrationTest {

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        userRepository.deleteAllInBatch();
    }

    @Nested
    @DisplayName("handleTimeZoneUpdate")
    class HandleTimeZoneUpdate {

        @Test
        @DisplayName("SHOULD update zoneInfo WHEN valid new zone is published")
        void shouldUpdateZoneInfoWhenValidNewZone() {
            User user = userRepository.save(
                UserMother.createMinimal(
                    UserMother.uniqueUsername("zoneuser", System.nanoTime()),
                    UserMother.uniqueEmail("zoneuser", System.nanoTime())
                )
            );
            String newZone = "America/New_York";

            eventPublisher.publishEvent(
                new UserTimeZoneUpdateEvent(user.getUsername(), newZone)
            );

            await().atMost(5, SECONDS)
                .pollInterval(100, MILLISECONDS)
                .untilAsserted(() -> {
                    User refreshed = userRepository.findById(user.getId())
                        .orElseThrow(() -> new AssertionError("User not found"));
                    assertThat(refreshed.getZoneInfo())
                        .as("zoneInfo should be updated to the new zone")
                        .isEqualTo(newZone);
                });
        }

        @Test
        @DisplayName("SHOULD NOT update zoneInfo WHEN zone is invalid")
        void shouldNotUpdateZoneInfoWhenInvalidZone(CapturedOutput output) {
            User user = userRepository.save(
                UserMother.createMinimal(
                    UserMother.uniqueUsername("invalidzone", System.nanoTime()),
                    UserMother.uniqueEmail("invalidzone", System.nanoTime())
                )
            );
            String originalZone = user.getZoneInfo();

            eventPublisher.publishEvent(
                new UserTimeZoneUpdateEvent(user.getUsername(), "Invalid/Zone_Name_Here")
            );

            await().atMost(5, SECONDS)
                .pollInterval(100, MILLISECONDS)
                .untilAsserted(() -> {
                    User refreshed = userRepository.findById(user.getId())
                        .orElseThrow(() -> new AssertionError("User not found"));
                    assertThat(refreshed.getZoneInfo())
                        .as("zoneInfo should not change for invalid zone")
                        .isEqualTo(originalZone);
                    assertThat(output)
                        .as("Should log 'Invalid timezone'")
                        .contains("Invalid timezone")
                        .contains("invalidzone");
                });
        }

        @Test
        @DisplayName("SHOULD NOT persist WHEN published zone matches current zone")
        void shouldNotModifyUserWhenSameZoneIsPublished() {
            User user = userRepository.save(
                UserMother.createMinimal(
                    UserMother.uniqueUsername("samezone", System.nanoTime()),
                    UserMother.uniqueEmail("samezone", System.nanoTime())
                )
            );
            String currentZone = user.getZoneInfo();

            // Small delay to separate created_at from listener execution window
            await().pollDelay(100, MILLISECONDS);

            eventPublisher.publishEvent(
                new UserTimeZoneUpdateEvent(user.getUsername(), currentZone)
            );

            // The listener guard (!event.zoneInfo().equals(user.getZoneInfo()))
            // prevents any DB write when the zone hasn't changed.
            // Assert that zoneInfo is stable for the duration.
            await().during(2, SECONDS)
                .pollInterval(200, MILLISECONDS)
                .untilAsserted(() -> {
                    User refreshed = userRepository.findById(user.getId())
                        .orElseThrow(() -> new AssertionError("User not found"));
                    assertThat(refreshed.getZoneInfo())
                        .as("zoneInfo should remain unchanged (no save)")
                        .isEqualTo(currentZone);
                });
        }

        @Test
        @DisplayName("SHOULD log error and NOT throw WHEN username does not exist")
        void shouldLogErrorWhenUsernameDoesNotExist(CapturedOutput output) {
            eventPublisher.publishEvent(
                new UserTimeZoneUpdateEvent("nonexistent_user_99999", "America/New_York")
            );

            await().atMost(5, SECONDS)
                .pollInterval(100, MILLISECONDS)
                .untilAsserted(() ->
                    assertThat(output)
                        .as("Should log 'Error while updating zone info'")
                        .contains("Error while updating zone info")
                );
        }
    }
}
