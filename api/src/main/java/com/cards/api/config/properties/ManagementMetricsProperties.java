package com.cards.api.config.properties;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * HTTP Basic authentication credentials for the management/metrics endpoints.
 * <p>
 * Prefix: {@code management.metrics.auth.*}. Used when the management port differs from the main port.
 */
@Validated
@ConfigurationProperties(prefix = "management.metrics.auth")
public class ManagementMetricsProperties {

    @NotBlank
    private String username;
    @NotBlank
    private String password;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
