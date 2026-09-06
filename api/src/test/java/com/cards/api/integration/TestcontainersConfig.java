package com.cards.api.integration;

import org.springframework.boot.devtools.restart.RestartScope;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfig {

    /**
     * Bean del contenedor PostgreSQL con @ServiceConnection para autoconfiguración.
     *
     * <p>El atributo {@code @RestartScope} permite que el contenedor se reinicie
     * cuando se usa con spring-boot-devtools en modo de desarrollo de tests.</p>
     *
     * @return instancia configurada de PostgreSQLContainer
     */
    @Bean
    @RestartScope
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer("postgres:16-alpine")
                .withDatabaseName("flashcard_test")
                .withUsername("test")
                .withPassword("test")
                // Wait strategy explícita para mayor robustez en CI/CD
                .withStartupAttempts(3);
    }
}
