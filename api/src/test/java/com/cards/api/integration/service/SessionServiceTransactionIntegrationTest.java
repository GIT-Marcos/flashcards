package com.cards.api.integration.service;

import com.cards.api.integration.TestcontainersConfig;
import com.cards.api.service.SessionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.IllegalTransactionStateException;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
    "application.security.jwt.secret-key=integrationTestSecretKeyForHS256",
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
@DisplayName("SessionService @Transactional(MANDATORY)")
class SessionServiceTransactionIntegrationTest {

    @Autowired
    private SessionService sessionService;

    @Nested
    @DisplayName("updateMetrics propagation")
    class UpdateMetricsPropagation {

        @Test
        @DisplayName("should throw IllegalTransactionStateException when called without transaction")
        void shouldThrowWhenNoTransaction() {
            assertThatThrownBy(() -> sessionService.updateMetrics(1L, 3, Instant.now()))
                .isInstanceOf(IllegalTransactionStateException.class);
        }
    }
}
