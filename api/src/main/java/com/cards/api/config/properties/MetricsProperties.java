package com.cards.api.config.properties;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Custom application metrics configuration.
 * <p>
 * Prefix: {@code app.metrics.*}. Controls metric windows and thresholds.
 */
@Validated
@ConfigurationProperties(prefix = "app.metrics")
public class MetricsProperties {

    @Min(1)
    private int activeUsersWindowDays = 30;

    public int getActiveUsersWindowDays() {
        return activeUsersWindowDays;
    }

    public void setActiveUsersWindowDays(int activeUsersWindowDays) {
        this.activeUsersWindowDays = activeUsersWindowDays;
    }
}
