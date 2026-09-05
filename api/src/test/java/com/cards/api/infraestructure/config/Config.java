package com.cards.api.infraestructure.config;

import com.cards.api.config.properties.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;

@TestConfiguration(proxyBeanMethods = false)
@EnableConfigurationProperties({
    ApplicationProperties.class,
    AsyncProperties.class,
    CorsProperties.class,
    MailerooProperties.class,
    ManagementMetricsProperties.class,
    MetricsProperties.class,
    RateLimitingConfig.class
})
public class Config {

}
