package com.cards.api.smoke;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.cards.api.dto.request.CreateDeckRequest;
import com.cards.api.dto.request.LoginRequest;
import com.cards.api.dto.request.RegisterRequest;
import com.cards.api.dto.response.AuthResponse;
import com.cards.api.dto.response.DeckResponse;
import com.cards.api.dto.response.UserResponse;
import com.cards.api.entity.User;
import com.cards.api.integration.TestcontainersConfig;
import com.cards.api.repo.UserRepository;
import com.cards.api.service.notification.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;

/**
 * End-to-end smoke test that verifies the security filter chain,
 * authentication flow, and authorization boundaries through real HTTP calls.
 *
 * <p>Uses {@code @SpringBootTest(webEnvironment = RANDOM_PORT)} with
 * {@link RestTemplate} to exercise the full request lifecycle:
 * HTTP request → Security filter → Controller → Service → Repository → Response.</p>
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "application.security.jwt.secret-key=smokeTestSecretKeyThatIsLongEnoughForHS256Algorithm!!",
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
        "rate-limiter.auth.login.capacity=10",
        "rate-limiter.auth.login.refill-tokens=5",
        "rate-limiter.auth.login.refill-period=10s",
        "rate-limiter.auth.refresh-token.capacity=10",
        "rate-limiter.auth.refresh-token.refill-tokens=5",
        "rate-limiter.auth.refresh-token.refill-period=10s",
        "rate-limiter.auth.logout.capacity=10",
        "rate-limiter.auth.logout.refill-tokens=5",
        "rate-limiter.auth.logout.refill-period=10s",
        "rate-limiter.auth.signup.capacity=10",
        "rate-limiter.auth.signup.refill-tokens=5",
        "rate-limiter.auth.signup.refill-period=10s",
        "rate-limiter.auth.confirm.capacity=100",
        "rate-limiter.auth.confirm.refill-tokens=10",
        "rate-limiter.auth.confirm.refill-period=10s",
        "application.security.secure-cookie=false",
        "application.security.same-site=Strict",
        "application.security.verification-token-expiration=86400000",
        "application.security.reset-token-expiration=900000",
        "maileroo.api-key=smoke-test-api-key",
        "maileroo.webhook-secret=smoke-test-webhook-secret"
    }
)
@Import(TestcontainersConfig.class)
@DisplayName("Smoke: Security & End-to-End Auth Flow")
class SecuritySmokeTest {

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private EmailService emailService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    @BeforeEach
    void cleanUp() {
        userRepository.deleteAll();
        doNothing().when(emailService).sendVerificationEmail(anyString(), anyString(), anyString());
    }

    private void createUserDirectly(String username, String email, String password) {
        User user = User.builder()
            .username(username)
            .email(email)
            .passwordHash(passwordEncoder.encode(password))
            .roles(Set.of(User.UserRole.ROLE_USER))
            .zoneInfo("America/Argentina/Buenos_Aires")
            .build();
        userRepository.save(user);
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    // ================================================================== //
    //  Public endpoints (no auth required)
    // ================================================================== //

    @Nested
    @DisplayName("Public endpoints")
    class PublicEndpoints {

        @Test
        @DisplayName("POST /auth/login should be accessible without authentication")
        void authEndpointsShouldBePublic() {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            LoginRequest loginReq = new LoginRequest("test", "Test1234!");

            try {
                restTemplate.exchange(url("/auth/login"), HttpMethod.POST,
                    new HttpEntity<>(loginReq, headers), String.class);
            } catch (HttpClientErrorException e) {
                // 401 means the request reached the controller (public endpoint) but credentials were wrong
                // 403 would mean the security filter blocked it
                assertThat(e.getStatusCode())
                    .as("Public endpoint should not return 403 (security block)")
                    .isNotEqualTo(HttpStatus.FORBIDDEN);
            }
        }
    }

    // ================================================================== //
    //  Protected endpoints (auth required)
    // ================================================================== //

    @Nested
    @DisplayName("Protected endpoints — authentication required")
    class ProtectedEndpoints {

        @Test
        @DisplayName("GET /users/me should return 403 without JWT")
        void usersMeShouldReturn403WithoutToken() {
            assertThatThrownBy(() -> restTemplate.getForEntity(url("/users/me"), String.class))
                .isInstanceOf(HttpClientErrorException.class)
                .satisfies(e -> assertThat(((HttpClientErrorException) e).getStatusCode())
                    .isEqualTo(HttpStatus.FORBIDDEN));
        }

        @Test
        @DisplayName("GET /decks should return 403 without JWT")
        void decksShouldReturn403WithoutToken() {
            assertThatThrownBy(() -> restTemplate.getForEntity(url("/decks"), String.class))
                .isInstanceOf(HttpClientErrorException.class)
                .satisfies(e -> assertThat(((HttpClientErrorException) e).getStatusCode())
                    .isEqualTo(HttpStatus.FORBIDDEN));
        }

        @Test
        @DisplayName("GET /cards should return 403 without JWT")
        void cardsShouldReturn403WithoutToken() {
            assertThatThrownBy(() -> restTemplate.getForEntity(url("/cards/deck/1/pending"), String.class))
                .isInstanceOf(HttpClientErrorException.class)
                .satisfies(e -> assertThat(((HttpClientErrorException) e).getStatusCode())
                    .isEqualTo(HttpStatus.FORBIDDEN));
        }

        @Test
        @DisplayName("GET /reviews should return 403 without JWT")
        void reviewsShouldReturn403WithoutToken() {
            assertThatThrownBy(() -> restTemplate.getForEntity(url("/reviews/1"), String.class))
                .isInstanceOf(HttpClientErrorException.class)
                .satisfies(e -> assertThat(((HttpClientErrorException) e).getStatusCode())
                    .isEqualTo(HttpStatus.FORBIDDEN));
        }

        @Test
        @DisplayName("GET /sessions should return 403 without JWT")
        void sessionsShouldReturn403WithoutToken() {
            assertThatThrownBy(() -> restTemplate.getForEntity(url("/sessions"), String.class))
                .isInstanceOf(HttpClientErrorException.class)
                .satisfies(e -> assertThat(((HttpClientErrorException) e).getStatusCode())
                    .isEqualTo(HttpStatus.FORBIDDEN));
        }
    }

    // ================================================================== //
    //  Admin endpoints (admin role required)
    // ================================================================== //

    @Nested
    @DisplayName("Admin endpoints — admin role required")
    class AdminEndpoints {

        @Test
        @DisplayName("GET /admin/users should return 403 for unauthenticated user")
        void adminUsersShouldReturn403ForUnauthenticated() {
            assertThatThrownBy(() -> restTemplate.getForEntity(url("/admin/users"), String.class))
                .isInstanceOf(HttpClientErrorException.class)
                .satisfies(e -> assertThat(((HttpClientErrorException) e).getStatusCode())
                    .isEqualTo(HttpStatus.FORBIDDEN));
        }
    }

    // ================================================================== //
    //  Signup flow
    // ================================================================== //

    @Nested
    @DisplayName("Signup flow")
    class SignupFlow {

        @Test
        @DisplayName("POST /auth/signup should return 202 with message")
        void signupShouldReturnAccepted() {
            String uniqueSuffix = String.valueOf(System.nanoTime());
            RegisterRequest request = new RegisterRequest(
                "signupuser_" + uniqueSuffix,
                "signup_" + uniqueSuffix + "@test.com",
                "ValidPass123!",
                "America/Argentina/Buenos_Aires"
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            ResponseEntity<String> response = restTemplate.exchange(
                url("/auth/signup"),
                HttpMethod.POST,
                new HttpEntity<>(request, headers),
                String.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
            assertThat(response.getBody()).contains("email");
        }

        @Test
        @DisplayName("POST /auth/signup should return 409 for duplicate username")
        void signupShouldReturn409ForDuplicate() {
            String uniqueSuffix = String.valueOf(System.nanoTime());
            String username = "dup_" + uniqueSuffix;

            createUserDirectly(username, "dup_" + uniqueSuffix + "@test.com", "ValidPass123!");

            RegisterRequest request = new RegisterRequest(
                username,
                "other_" + uniqueSuffix + "@test.com",
                "ValidPass123!",
                "America/Argentina/Buenos_Aires"
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            assertThatThrownBy(() -> restTemplate.exchange(
                url("/auth/signup"),
                HttpMethod.POST,
                new HttpEntity<>(request, headers),
                String.class
            ))
                .isInstanceOf(HttpClientErrorException.class)
                .satisfies(e -> assertThat(((HttpClientErrorException) e).getStatusCode())
                    .isEqualTo(HttpStatus.CONFLICT));
        }

        @Test
        @DisplayName("POST /auth/signup should return 400 for validation errors")
        void signupShouldReturn400ForValidationErrors() {
            RegisterRequest request = new RegisterRequest(
                "",
                "not-an-email",
                "123",
                "Invalid/Zone"
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            assertThatThrownBy(() -> restTemplate.exchange(
                url("/auth/signup"),
                HttpMethod.POST,
                new HttpEntity<>(request, headers),
                String.class
            ))
                .isInstanceOf(HttpClientErrorException.class)
                .satisfies(e -> assertThat(((HttpClientErrorException) e).getStatusCode())
                    .isEqualTo(HttpStatus.BAD_REQUEST));
        }
    }

    // ================================================================== //
    //  Full authentication flow: login -> access protected
    // ================================================================== //

    @Nested
    @DisplayName("Full authentication flow")
    class FullAuthFlow {

        @Test
        @DisplayName("login -> access /users/me should return 200")
        void fullAuthFlowShouldWork() {
            String uniqueSuffix = String.valueOf(System.nanoTime());
            String username = "flow_" + uniqueSuffix;
            String email = "flow_" + uniqueSuffix + "@test.com";

            createUserDirectly(username, email, "ValidPass123!");

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            LoginRequest loginReq = new LoginRequest(username, "ValidPass123!");
            ResponseEntity<AuthResponse> loginResponse = restTemplate.exchange(
                url("/auth/login"),
                HttpMethod.POST,
                new HttpEntity<>(loginReq, headers),
                AuthResponse.class
            );
            assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

            String accessToken = loginResponse.getBody().accessToken();
            assertThat(accessToken).isNotNull().isNotEmpty();

            HttpHeaders authHeaders = new HttpHeaders();
            authHeaders.setBearerAuth(accessToken);

            ResponseEntity<UserResponse> profileResponse = restTemplate.exchange(
                url("/users/me"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders),
                UserResponse.class
            );

            assertThat(profileResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
            UserResponse profile = profileResponse.getBody();
            assertThat(profile).isNotNull();
            assertThat(profile.username()).isEqualTo(username);
            assertThat(profile.email()).isEqualTo(email);
            assertThat(profile.roles()).contains("ROLE_USER");
            assertThat(profile.createdAt()).isNotNull();
        }

        @Test
        @DisplayName("login -> create deck should work end-to-end")
        void fullCrudFlowShouldWork() {
            String uniqueSuffix = String.valueOf(System.nanoTime());
            String username = "crud_" + uniqueSuffix;

            createUserDirectly(username, "crud_" + uniqueSuffix + "@test.com", "ValidPass123!");

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            LoginRequest loginReq = new LoginRequest(username, "ValidPass123!");
            ResponseEntity<AuthResponse> loginResponse = restTemplate.exchange(
                url("/auth/login"),
                HttpMethod.POST,
                new HttpEntity<>(loginReq, headers),
                AuthResponse.class
            );
            assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

            String accessToken = loginResponse.getBody().accessToken();
            assertThat(accessToken).isNotNull().isNotEmpty();

            HttpHeaders authHeaders = new HttpHeaders();
            authHeaders.setContentType(MediaType.APPLICATION_JSON);
            authHeaders.setBearerAuth(accessToken);

            CreateDeckRequest deckReq = new CreateDeckRequest("Test Deck");
            ResponseEntity<DeckResponse> createDeckResponse = restTemplate.exchange(
                url("/decks"),
                HttpMethod.POST,
                new HttpEntity<>(deckReq, authHeaders),
                DeckResponse.class
            );
            assertThat(createDeckResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(createDeckResponse.getBody()).isNotNull();
            assertThat(createDeckResponse.getBody().name()).isEqualTo("Test Deck");
        }
    }

    // ================================================================== //
    //  Logout flow
    // ================================================================== //

    @Nested
    @DisplayName("Logout flow")
    class LogoutFlow {

        @Test
        @DisplayName("POST /auth/logout should return 204 and clear cookie")
        void logoutShouldReturn204AndClearCookie() {
            ResponseEntity<String> response = restTemplate.exchange(
                url("/auth/logout"),
                HttpMethod.POST,
                null,
                String.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

            String setCookie = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
            assertThat(setCookie).isNotNull();
            assertThat(setCookie).contains("Max-Age=0");
        }
    }

    // ================================================================== //
    //  Authentication failures
    // ================================================================== //

    @Nested
    @DisplayName("Authentication failures")
    class AuthenticationFailures {

        @Test
        @DisplayName("POST /auth/login should return 401 for wrong password")
        void loginShouldReturn401ForWrongPassword() {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            LoginRequest loginReq = new LoginRequest("doesnotexist", "WrongPass123!");

            assertThatThrownBy(() -> restTemplate.exchange(
                url("/auth/login"),
                HttpMethod.POST,
                new HttpEntity<>(loginReq, headers),
                String.class
            ))
                .isInstanceOf(HttpClientErrorException.class)
                .satisfies(e -> assertThat(((HttpClientErrorException) e).getStatusCode())
                    .isEqualTo(HttpStatus.UNAUTHORIZED));
        }

        @Test
        @DisplayName("GET /users/me should return 401 with invalid JWT")
        void protectedEndpointShouldRejectInvalidJwt() {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth("invalid-jwt-token");

            assertThatThrownBy(() -> restTemplate.exchange(
                url("/users/me"),
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class
            ))
                .isInstanceOf(HttpClientErrorException.class)
                .satisfies(e -> assertThat(((HttpClientErrorException) e).getStatusCode())
                    .isEqualTo(HttpStatus.UNAUTHORIZED));
        }
    }
}
