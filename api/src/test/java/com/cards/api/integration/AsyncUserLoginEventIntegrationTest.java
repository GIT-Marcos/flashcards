package com.cards.api.integration;

import com.cards.api.dto.event.UserLoginEvent;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
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
@DisplayName("Async event: UserLoginEvent")
class AsyncUserLoginEventIntegrationTest {

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() {
        transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Nested
    @DisplayName("handleLastLoginUpdate")
    class HandleLastLoginUpdate {

        @Test
        @DisplayName("SHOULD update lastLogin WHEN UserLoginEvent is published for an existing user")
        void shouldUpdateLastLoginWhenEventIsPublished() {
            User user = userRepository.save(
                UserMother.createMinimal(
                    UserMother.uniqueUsername("loginuser", System.nanoTime()),
                    UserMother.uniqueEmail("loginuser", System.nanoTime())
                )
            );
            Long userId = user.getId();
            assertThat(user.getLastLogin()).isNull();

            Instant beforePublish = Instant.now();
            transactionTemplate.executeWithoutResult(status ->
                eventPublisher.publishEvent(new UserLoginEvent(userId))
            );

            await().atMost(5, SECONDS)
                .pollInterval(100, MILLISECONDS)
                .untilAsserted(() -> {
                    User refreshed = userRepository.findById(userId)
                        .orElseThrow(() -> new AssertionError("User not found after event"));
                    assertThat(refreshed.getLastLogin())
                        .as("lastLogin should have been updated by the async listener")
                        .isNotNull();
                    assertThat(refreshed.getLastLogin())
                        .as("Timestamp should be close to the publish moment")
                        .isCloseTo(beforePublish, within(2, ChronoUnit.SECONDS));
                });
        }

        @Test
        @DisplayName("SHOULD log warning and NOT propagate exception WHEN userId does not exist")
        void shouldLogWarningWhenUserDoesNotExist(CapturedOutput output) {
            Long nonExistentId = 99999L;

            transactionTemplate.executeWithoutResult(status ->
                eventPublisher.publishEvent(new UserLoginEvent(nonExistentId))
            );

            await().atMost(5, SECONDS)
                .pollInterval(100, MILLISECONDS)
                .untilAsserted(() ->
                    assertThat(output)
                        .as("Should log warning for non-existent user")
                        .contains("Could not update lastLogin")
                        .contains(String.valueOf(nonExistentId))
                );
        }
    }
}
