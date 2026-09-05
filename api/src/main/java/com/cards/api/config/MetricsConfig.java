package com.cards.api.config;

import com.cards.api.config.properties.MetricsProperties;
import com.cards.api.repo.UserRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Configuration
public class MetricsConfig {

    private final Clock clock;
    private final MetricsProperties properties;

    public MetricsConfig(Clock clock, MetricsProperties properties) {
        this.clock = clock;
        this.properties = properties;
    }

    @Bean
    public Counter decksCreatedCounter(MeterRegistry meterRegistry) {
        return Counter.builder("flashcards.decks.created.total")
            .description("Total number of decks created")
            .register(meterRegistry);
    }

    @Bean
    public Counter failedEmailsCounter(MeterRegistry meterRegistry) {
        return Counter.builder("flashcards.email.failed.total")
            .description("Total number of failed email sends after retries")
            .register(meterRegistry);
    }

    @Bean
    public Timer emailDeliveryTimer(MeterRegistry meterRegistry) {
        return Timer.builder("flashcards.email.delivery.time")
            .description("Time to send an email including retries")
            .publishPercentileHistogram()
            .sla(Duration.ofMillis(500),
                Duration.ofMillis(1000),
                Duration.ofMillis(2000),
                Duration.ofMillis(5000))
            .register(meterRegistry);
    }

    @Bean
    public Gauge activeUsersGauge(MeterRegistry meterRegistry, UserRepository userRepository) {
        return Gauge.builder("flashcards.users.active.count",
                () -> userRepository.countActiveUsersSince(
                    Instant.now(clock).minus(Duration.ofDays(properties.getActiveUsersWindowDays()))
                )
            )
            .description("Number of users who logged in within the last " + properties.getActiveUsersWindowDays() + " days")
            .register(meterRegistry);
    }
}
