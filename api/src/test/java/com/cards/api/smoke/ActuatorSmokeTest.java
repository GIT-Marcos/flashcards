package com.cards.api.smoke;

import com.cards.api.integration.TestcontainersConfig;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
    "application.security.jwt.secret-key=smokeTestSecretKeyThatIsLongEnoughForHS256Algorithm!!",
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
    "maileroo.api-key=smoke-test-api-key",
    "maileroo.webhook-secret=smoke-test-webhook-secret",
    "application.security.verification-token-expiration=86400000",
    "application.security.reset-token-expiration=900000"
})
@Import({TestcontainersConfig.class})
@DisplayName("Smoke: Actuator and Metrics")
class ActuatorSmokeTest {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private MeterRegistry meterRegistry;

    @Test
    @DisplayName("Application context should load with actuator")
    void contextLoadsWithActuator() {
        assertThat(context).isNotNull();
    }

    @Test
    @DisplayName("MeterRegistry bean should be available")
    void meterRegistryIsAvailable() {
        assertThat(meterRegistry).isNotNull();
    }

    @Test
    @DisplayName("deck created counter should be registered")
    void decksCreatedCounterRegistered() {
        var counter = meterRegistry.find("flashcards.decks.created.total").counter();
        assertThat(counter).isNotNull();
    }

    @Test
    @DisplayName("email delivery timer should be registered")
    void emailDeliveryTimerRegistered() {
        var timer = meterRegistry.find("flashcards.email.delivery.time").timer();
        assertThat(timer).isNotNull();
    }

    @Test
    @DisplayName("active users gauge should be registered")
    void activeUsersGaugeRegistered() {
        var gauge = meterRegistry.find("flashcards.users.active.count").gauge();
        assertThat(gauge).isNotNull();
    }
}
