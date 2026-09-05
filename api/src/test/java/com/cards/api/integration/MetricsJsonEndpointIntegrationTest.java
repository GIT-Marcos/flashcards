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

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "spring.profiles.active=dev",
        "management.endpoints.web.exposure.include=health,info,prometheus,metrics,env,threaddump,loggers",
        "application.security.jwt.secret-key=metricsJsonTestSecretKeyThatIsLongEnoughForHS256!!",
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
        "maileroo.api-key=metrics-json-test-api-key",
        "maileroo.webhook-secret=metrics-json-test-webhook-secret",
        "rate-limiter.auth.login.capacity=100",
        "rate-limiter.auth.login.refill-tokens=100",
        "rate-limiter.auth.login.refill-period=1s",
        "application.security.verification-token-expiration=86400000",
        "application.security.reset-token-expiration=900000"
    }
)
@Import(TestcontainersConfig.class)
@AutoConfigureRestTestClient
@DisplayName("Integration: Metrics JSON Endpoint")
class MetricsJsonEndpointIntegrationTest {

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
        String username = "metricsadmin_" + uniqueSuffix;
        String email = "metricsadmin_" + uniqueSuffix + "@test.com";
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

    private RestTestClient.RequestHeadersSpec<?> adminGet(String uri) {
        return restClient
            .get()
            .uri(uri)
            .header("Authorization", "Bearer " + adminAccessToken);
    }

    @Nested
    @DisplayName("GET /actuator/metrics (list all metric names)")
    class MetricsListEndpoint {

        @Test
        @DisplayName("should return 403 without authentication")
        void shouldReturn403WithoutAuth() {
            restClient
                .get()
                .uri("/actuator/metrics")
                .exchange()
                .expectStatus()
                .isForbidden();
        }

        @Test
        @DisplayName("should return 200 with admin JWT")
        void shouldReturn200WithAdminJwt() {
            adminGet("/actuator/metrics")
                .exchange()
                .expectStatus()
                .isOk();
        }

        @Test
        @DisplayName("should return JSON with 'names' array")
        void shouldReturnNamesArray() {
            Map<String, Object> body = adminGet("/actuator/metrics")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody();

            assertThat(body).isNotNull();
            assertThat(body).containsKey("names");

            @SuppressWarnings("unchecked")
            List<String> names = (List<String>) body.get("names");
            assertThat(names).isNotEmpty();
        }

        @Test
        @DisplayName("should contain custom application metric names")
        void shouldContainCustomMetricNames() {
            Counter counter = meterRegistry.find("flashcards.decks.created.total").counter();
            assertThat(counter).isNotNull();
            counter.increment();

            Map<String, Object> body = adminGet("/actuator/metrics")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody();

            @SuppressWarnings("unchecked")
            List<String> names = (List<String>) body.get("names");

            assertThat(names).anyMatch(n -> n.contains("flashcards"));
            assertThat(names).anyMatch(n -> n.contains("decks"));
        }

        @Test
        @DisplayName("should contain HTTP server request metrics")
        void shouldContainHttpMetrics() {
            Map<String, Object> body = adminGet("/actuator/metrics")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody();

            @SuppressWarnings("unchecked")
            List<String> names = (List<String>) body.get("names");

            assertThat(names).anyMatch(n -> n.contains("http.server"));
        }
    }

    @Nested
    @DisplayName("GET /actuator/metrics/{metricName} (single metric detail)")
    class SingleMetricEndpoint {

        @Test
        @DisplayName("should return metric detail with measurements")
        void shouldReturnMetricDetail() {
            Counter counter = meterRegistry.find("flashcards.decks.created.total").counter();
            assertThat(counter).isNotNull();
            counter.increment(5);

            Map<String, Object> body = adminGet("/actuator/metrics/flashcards.decks.created.total")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody();

            assertThat(body).isNotNull();
            assertThat(body.get("name")).isEqualTo("flashcards.decks.created.total");
            assertThat(body).containsKey("measurements");

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> measurements = (List<Map<String, Object>>) body.get("measurements");
            assertThat(measurements).isNotEmpty();

            assertThat(measurements)
                .anyMatch(m -> "COUNT".equals(m.get("statistic")));
        }

        @Test
        @DisplayName("should return 404 for non-existent metric")
        void shouldReturn404ForNonExistentMetric() {
            restClient
                .get()
                .uri("/actuator/metrics/nonexistent.metric.name")
                .header("Authorization", "Bearer " + adminAccessToken)
                .exchange()
                .expectStatus()
                .isNotFound();
        }

        @Test
        @DisplayName("should return 403 for single metric without auth")
        void shouldReturn403ForSingleMetricWithoutAuth() {
            restClient
                .get()
                .uri("/actuator/metrics/flashcards.decks.created.total")
                .exchange()
                .expectStatus()
                .isForbidden();
        }
    }
}
