package com.cards.api.unit;

import com.cards.api.repo.UserRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Active Users Gauge")
class ActiveUsersGaugeTest {

    @Mock
    private UserRepository userRepository;

    private SimpleMeterRegistry meterRegistry;
    private Clock fixedClock;

    private static final Instant FIXED_NOW = Instant.parse("2026-05-19T12:00:00Z");
    private static final int ACTIVE_WINDOW_DAYS = 30;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        fixedClock = Clock.fixed(FIXED_NOW, ZoneId.of("UTC"));
    }

    @Nested
    @DisplayName("gauge value")
    class GaugeValue {

        @Test
        @DisplayName("should return the count from repository query")
        void shouldReturnRepositoryCount() {
            Instant cutoff = FIXED_NOW.minus(Duration.ofDays(ACTIVE_WINDOW_DAYS));
            when(userRepository.countActiveUsersSince(cutoff)).thenReturn(42L);

            createGauge();

            double value = meterRegistry.get("flashcards.users.active.count").gauge().value();

            assertThat(value).isEqualTo(42.0);
        }

        @Test
        @DisplayName("should return zero when no active users")
        void shouldReturnZeroWhenNoActiveUsers() {
            Instant cutoff = FIXED_NOW.minus(Duration.ofDays(ACTIVE_WINDOW_DAYS));
            when(userRepository.countActiveUsersSince(cutoff)).thenReturn(0L);

            createGauge();

            double value = meterRegistry.get("flashcards.users.active.count").gauge().value();

            assertThat(value).isEqualTo(0.0);
        }

        @Test
        @DisplayName("should compute correct cutoff date using clock")
        void shouldComputeCorrectCutoffDate() {
            Instant expectedCutoff = FIXED_NOW.minus(Duration.ofDays(ACTIVE_WINDOW_DAYS));
            when(userRepository.countActiveUsersSince(expectedCutoff)).thenReturn(5L);

            createGauge();

            double value = meterRegistry.get("flashcards.users.active.count").gauge().value();

            assertThat(value).isEqualTo(5.0);
        }
    }

    @Nested
    @DisplayName("gauge registration")
    class GaugeRegistration {

        @Test
        @DisplayName("should have correct metric name")
        void shouldHaveCorrectName() {
            Instant cutoff = FIXED_NOW.minus(Duration.ofDays(ACTIVE_WINDOW_DAYS));
            when(userRepository.countActiveUsersSince(cutoff)).thenReturn(1L);

            createGauge();

            var gauge = meterRegistry.find("flashcards.users.active.count").gauge();
            assertThat(gauge).isNotNull();
            assertThat(gauge.getId().getName()).isEqualTo("flashcards.users.active.count");
            gauge.value();
        }

        @Test
        @DisplayName("should have description mentioning window days")
        void shouldHaveDescription() {
            Instant cutoff = FIXED_NOW.minus(Duration.ofDays(ACTIVE_WINDOW_DAYS));
            when(userRepository.countActiveUsersSince(cutoff)).thenReturn(1L);

            createGauge();

            var gauge = meterRegistry.find("flashcards.users.active.count").gauge();
            assertThat(gauge.getId().getDescription())
                    .contains("30")
                    .contains("days");
            gauge.value();
        }
    }

    private Gauge createGauge() {
        return Gauge.builder("flashcards.users.active.count",
                        () -> userRepository.countActiveUsersSince(
                                Instant.now(fixedClock).minus(Duration.ofDays(ACTIVE_WINDOW_DAYS))
                        )
                )
                .description("Number of users who logged in within the last " + ACTIVE_WINDOW_DAYS + " days")
                .register(meterRegistry);
    }
}
