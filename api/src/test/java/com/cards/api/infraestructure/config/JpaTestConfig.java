package com.cards.api.infraestructure.config;

import com.cards.api.config.properties.EncryptionProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;

@TestConfiguration(proxyBeanMethods = false)
@EnableConfigurationProperties(EncryptionProperties.class)
public class JpaTestConfig {
}
