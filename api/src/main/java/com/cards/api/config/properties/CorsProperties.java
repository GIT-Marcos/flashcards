package com.cards.api.config.properties;

import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * CORS (Cross-Origin Resource Sharing) configuration.
 * <p>
 * Prefix: {@code api.cors.*}. Defines allowed origins for frontend access.
 */
@Validated
@ConfigurationProperties(prefix = "api.cors")
public class CorsProperties {

    @NotEmpty
    private List<String> allowedOrigins = List.of("http://localhost:5173");

    public List<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }
}
