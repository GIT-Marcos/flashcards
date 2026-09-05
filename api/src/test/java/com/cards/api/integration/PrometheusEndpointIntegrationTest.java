package com.cards.api.integration;

import com.cards.api.entity.User;
import com.cards.api.repo.UserRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.client.RestTestClient;

import java.util.Map;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "spring.profiles.active=dev",
        "management.endpoints.web.exposure.include=health,info,prometheus,metrics,env,threaddump,loggers",
        "application.security.jwt.secret-key=prometheusTestSecretKeyThatIsLongEnoughForHS256!!",
        "application.security.jwt.expiration=900000",
        "application.security.jwt.refresh-token.expiration=604800000",
        "application.notifications.send-at-hour=9",
        "application.notifications.threshold-hours=20",
        "application.notifications.cron=0 0 0 * * *",
        "application.notifications.app-url=http://localhost:5173",
        "application.notifications.from-address=notificaciones@flashcards.app",
        "application.notifications.from-name=Flashcards App",
        "application.notifications.unsubscribe-token-expiration=2592000000",
        "application.notifications.api-url=http://localhost:8080",
        "application.security.secure-cookie=false",
        "application.security.same-site=Strict",
        "maileroo.api-key=prometheus-test-api-key",
        "maileroo.webhook-secret=prometheus-test-webhook-secret",
        "rate-limiter.auth.login.capacity=100",
        "rate-limiter.auth.login.refill-tokens=100",
        "rate-limiter.auth.login.refill-period=1s",
        "application.security.verification-token-expiration=86400000",
        "application.security.reset-token-expiration=900000"
    }
)
@Import(TestcontainersConfig.class)
@AutoConfigureRestTestClient
@DisplayName("Integration: Prometheus Endpoint")
class PrometheusEndpointIntegrationTest {

    @Autowired
    private RestTestClient restClient;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private static final String ADMIN_PASSWORD = "Admin123!";

    private String adminAccessToken;

    @BeforeEach
    void setUp() {
        String uniqueSuffix = String.valueOf(System.nanoTime());
        String username = "promadmin_" + uniqueSuffix;
        String email = "promadmin_" + uniqueSuffix + "@test.com";

        String passwordHash = passwordEncoder.encode(ADMIN_PASSWORD);

        User admin = User.builder()
            .username(username)
            .email(email)
            .passwordHash(passwordHash)
            .zoneInfo("UTC")
            .addRole(User.UserRole.ROLE_ADMIN)
            .addRole(User.UserRole.ROLE_USER)
            .build();

        userRepository.save(admin);

        Map<String, String> loginBody = Map.of(
            "username", username,
            "password", ADMIN_PASSWORD
        );

        adminAccessToken = restClient
            .post()
            .uri("/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .body(loginBody)
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(Map.class)
            .returnResult()
            .getResponseBody()
            .get("accessToken")
            .toString();
    }

    @Nested
    @DisplayName("GET /actuator/prometheus")
    class PrometheusEndpoint {

        @Test
        @DisplayName("should return 403 without authentication")
        void shouldReturn403WithoutAuth() {
            restClient
                .get()
                .uri("/actuator/prometheus")
                .exchange()
                .expectStatus()
                .isForbidden();
        }

        @Test
        @DisplayName("should return 200 with admin JWT")
        void shouldReturn200WithAdminJwt() {
            restClient
                .get()
                .uri("/actuator/prometheus")
                .header("Authorization", "Bearer " + adminAccessToken)
                .exchange()
                .expectStatus()
                .isOk();
        }

        @Test
        @DisplayName("should return Prometheus exposition format (text/plain)")
        void shouldReturnPrometheusFormat() {
            restClient
                .get()
                .uri("/actuator/prometheus")
                .header("Authorization", "Bearer " + adminAccessToken)
                .exchange()
                .expectStatus()
                .isOk()
                .expectHeader()
                .contentTypeCompatibleWith(MediaType.TEXT_PLAIN);
        }

        @Test
        @DisplayName("should contain custom application metrics in output")
        void shouldContainCustomMetrics() {
            Counter counter = meterRegistry.find("flashcards.decks.created.total").counter();
            assert counter != null;
            counter.increment(3);

            String body = restClient
                .get()
                .uri("/actuator/prometheus")
                .header("Authorization", "Bearer " + adminAccessToken)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();

            assert body != null;
            org.assertj.core.api.Assertions.assertThat(body).contains("flashcards_decks_total");
            org.assertj.core.api.Assertions.assertThat(body).contains("3.0");
        }

        @Test
        @DisplayName("should contain JVM metrics")
        void shouldContainJvmMetrics() {
            String body = restClient
                .get()
                .uri("/actuator/prometheus")
                .header("Authorization", "Bearer " + adminAccessToken)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();

            assert body != null;
            org.assertj.core.api.Assertions.assertThat(body).contains("jvm");
        }
    }
}
