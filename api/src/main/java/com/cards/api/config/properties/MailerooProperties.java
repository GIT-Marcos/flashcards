package com.cards.api.config.properties;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Maileroo email API configuration.
 * <p>
 * Prefix: {@code maileroo.*}. Holds the API key and webhook signing secret.
 */
@Validated
@ConfigurationProperties(prefix = "maileroo")
public class MailerooProperties {

    @NotBlank
    private String apiKey;
    @NotBlank
    private String webhookSecret;

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getWebhookSecret() {
        return webhookSecret;
    }

    public void setWebhookSecret(String webhookSecret) {
        this.webhookSecret = webhookSecret;
    }
}
