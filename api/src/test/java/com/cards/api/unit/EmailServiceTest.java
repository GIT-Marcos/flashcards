package com.cards.api.unit;

import com.maileroo.MailerooClient;
import com.cards.api.config.properties.ApplicationProperties;
import com.cards.api.service.JwtService;
import com.cards.api.service.UserService;
import com.cards.api.service.notification.EmailService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("EmailService")
class EmailServiceTest {

    @Mock
    private MailerooClient mailerooClient;
    @Mock
    private UserService userService;
    @Mock
    private JwtService jwtService;
    @Mock
    private SpringTemplateEngine templateEngine;
    @Mock
    private ApplicationProperties properties;

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final Timer emailDeliveryTimer = Timer.builder("flashcards.email.delivery.time")
        .description("Time to send an email including retries")
        .publishPercentileHistogram()
        .register(meterRegistry);
    private final Counter failedEmailsCounter = Counter.builder("flashcards.email.failed.total")
        .register(meterRegistry);

    private EmailService emailService;
    private RetryTemplate retryTemplate;

    private static final Long USER_ID = 1L;
    private static final String EMAIL = "test@example.com";
    private static final String USERNAME = "testuser";
    private static final Instant NOW = Instant.now();

    @BeforeEach
    void setUp() {
        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy(
            2,
            Map.of(IOException.class, true),
            false
        );
        ExponentialBackOffPolicy backoff = new ExponentialBackOffPolicy();
        backoff.setInitialInterval(10);
        backoff.setMultiplier(1.0);
        backoff.setMaxInterval(10);

        retryTemplate = new RetryTemplate();
        retryTemplate.setRetryPolicy(retryPolicy);
        retryTemplate.setBackOffPolicy(backoff);

        lenient().when(jwtService.generateUnsubscribeToken(any())).thenReturn("test-unsubscribe-token");

        var notifications = new ApplicationProperties.Notifications();
        notifications.setApiUrl("http://localhost:8080");
        notifications.setAppUrl("http://localhost:5173");
        notifications.setFromAddress("notificaciones@flashcards.app");
        notifications.setFromName("Flashcards App");
        lenient().when(properties.getNotifications()).thenReturn(notifications);

        emailService = new EmailService(mailerooClient, userService, jwtService, retryTemplate, templateEngine, meterRegistry, emailDeliveryTimer, failedEmailsCounter, properties);
    }

    @Nested
    @DisplayName("sendReviewReminder")
    class SendReviewReminder {

        @Test
        @DisplayName("should send email and update timestamp on success")
        void shouldSendAndUpdateTimestamp() throws Exception {
            when(templateEngine.process(anyString(), any())).thenReturn("<html>body</html>");
            when(mailerooClient.sendBasicEmail(any(Map.class))).thenReturn("ref-id-123");

            emailService.sendReviewReminder(EMAIL, USERNAME, USER_ID, NOW);

            verify(mailerooClient).sendBasicEmail(any(Map.class));
            verify(templateEngine).process(anyString(), any());
            verify(userService).updateUserNotificationTimestamp(USER_ID, NOW);
            assertThat(failedEmailsCounter.count()).isEqualTo(0);
        }

        @Test
        @DisplayName("should NOT update timestamp when send fails after retries")
        void shouldNotUpdateTimestampOnFailure() throws Exception {
            when(templateEngine.process(anyString(), any())).thenReturn("<html>body</html>");
            when(mailerooClient.sendBasicEmail(any(Map.class))).thenThrow(new IOException("API timeout"));

            emailService.sendReviewReminder(EMAIL, USERNAME, USER_ID, NOW);

            verify(mailerooClient, times(2)).sendBasicEmail(any(Map.class));
            verify(userService, never()).updateUserNotificationTimestamp(any(), any());
            assertThat(failedEmailsCounter.count()).isEqualTo(1);
        }

        @Test
        @DisplayName("should succeed on retry after transient failure")
        void shouldSucceedOnRetry() throws Exception {
            when(templateEngine.process(anyString(), any())).thenReturn("<html>body</html>");
            when(mailerooClient.sendBasicEmail(any(Map.class)))
                .thenThrow(new IOException("First attempt failed"))
                .thenReturn("ref-id-456");

            emailService.sendReviewReminder(EMAIL, USERNAME, USER_ID, NOW);

            verify(mailerooClient, times(2)).sendBasicEmail(any(Map.class));
            verify(userService).updateUserNotificationTimestamp(USER_ID, NOW);
            assertThat(failedEmailsCounter.count()).isEqualTo(0);
        }

        @Test
        @DisplayName("should NOT retry on non-retryable exception")
        void shouldNotRetryOnNonRetryable() throws Exception {
            when(templateEngine.process(anyString(), any())).thenReturn("<html>body</html>");
            when(mailerooClient.sendBasicEmail(any(Map.class))).thenThrow(new RuntimeException("Unexpected error"));

            emailService.sendReviewReminder(EMAIL, USERNAME, USER_ID, NOW);

            verify(mailerooClient, times(1)).sendBasicEmail(any(Map.class));
            verify(userService, never()).updateUserNotificationTimestamp(any(), any());
            assertThat(failedEmailsCounter.count()).isEqualTo(1);
        }

        @Test
        @DisplayName("should record timer metric on successful email send")
        void shouldRecordTimerOnSuccess() throws Exception {
            when(templateEngine.process(anyString(), any())).thenReturn("<html>body</html>");
            when(mailerooClient.sendBasicEmail(any(Map.class))).thenReturn("ref-id-789");

            emailService.sendReviewReminder(EMAIL, USERNAME, USER_ID, NOW);

            var timer = meterRegistry.find("flashcards.email.delivery.time").timer();
            assertThat(timer).isNotNull();
            assertThat(timer.count()).isEqualTo(1);
            assertThat(timer.totalTime(TimeUnit.MILLISECONDS)).isGreaterThan(0);
        }

        @Test
        @DisplayName("should record timer metric even when email send fails")
        void shouldRecordTimerOnFailure() throws Exception {
            when(templateEngine.process(anyString(), any())).thenReturn("<html>body</html>");
            when(mailerooClient.sendBasicEmail(any(Map.class))).thenThrow(new IOException("API timeout"));

            emailService.sendReviewReminder(EMAIL, USERNAME, USER_ID, NOW);

            var timer = meterRegistry.find("flashcards.email.delivery.time").timer();
            assertThat(timer).isNotNull();
            assertThat(timer.count()).isEqualTo(1);
        }
    }
}
