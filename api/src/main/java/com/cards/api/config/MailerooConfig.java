package com.cards.api.config;

import com.maileroo.MailerooClient;
import com.cards.api.config.properties.MailerooProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;

@Configuration
public class MailerooConfig {

    private final MailerooProperties properties;

    public MailerooConfig(MailerooProperties properties) {
        this.properties = properties;
    }

    @Bean
    public MailerooClient mailerooClient() {
        return new MailerooClient(properties.getApiKey(), Duration.ofSeconds(30));
    }

    @Bean
    public RetryTemplate mailRetryTemplate() {
        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy(
            3,
            Map.of(
                IOException.class, true
            ),
            false
        );

        ExponentialBackOffPolicy backoff = new ExponentialBackOffPolicy();
        backoff.setInitialInterval(2000);
        backoff.setMultiplier(2.0);
        backoff.setMaxInterval(10000);

        RetryTemplate template = new RetryTemplate();
        template.setRetryPolicy(retryPolicy);
        template.setBackOffPolicy(backoff);
        return template;
    }
}
