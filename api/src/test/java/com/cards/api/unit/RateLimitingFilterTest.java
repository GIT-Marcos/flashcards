package com.cards.api.unit;

import com.cards.api.config.properties.RateLimitingConfig;
import com.cards.api.exception.TooManyRequestsException;
import com.cards.api.security.RateLimitingFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("RateLimitingFilter")
class RateLimitingFilterTest {

    @Mock
    private FilterChain filterChain;

    private RateLimitingFilter filter;
    private RateLimitingConfig config;

    @BeforeEach
    void setUp() {
        config = new RateLimitingConfig();

        RateLimitingConfig.Auth authConfig = new RateLimitingConfig.Auth();

        RateLimitingConfig.EndpointConfig loginConfig = new RateLimitingConfig.EndpointConfig();
        loginConfig.setCapacity(3);
        loginConfig.setRefillTokens(1);
        loginConfig.setRefillPeriod(Duration.ofSeconds(10));

        RateLimitingConfig.EndpointConfig refreshConfig = new RateLimitingConfig.EndpointConfig();
        refreshConfig.setCapacity(5);
        refreshConfig.setRefillTokens(1);
        refreshConfig.setRefillPeriod(Duration.ofSeconds(10));

        RateLimitingConfig.EndpointConfig signupConfig = new RateLimitingConfig.EndpointConfig();
        signupConfig.setCapacity(3);
        signupConfig.setRefillTokens(1);
        signupConfig.setRefillPeriod(Duration.ofSeconds(10));

        RateLimitingConfig.EndpointConfig confirmConfig = new RateLimitingConfig.EndpointConfig();
        confirmConfig.setCapacity(3);
        confirmConfig.setRefillTokens(1);
        confirmConfig.setRefillPeriod(Duration.ofSeconds(10));

        RateLimitingConfig.EndpointConfig logoutConfig = new RateLimitingConfig.EndpointConfig();
        logoutConfig.setCapacity(10);
        logoutConfig.setRefillTokens(1);
        logoutConfig.setRefillPeriod(Duration.ofSeconds(10));

        authConfig.setLogin(loginConfig);
        authConfig.setSignup(signupConfig);
        authConfig.setConfirm(confirmConfig);
        authConfig.setRefreshToken(refreshConfig);
        authConfig.setLogout(logoutConfig);

        config.setAuth(authConfig);

        filter = new RateLimitingFilter(config);
    }

    @Nested
    @DisplayName("login endpoint")
    class LoginEndpoint {

        @Test
        @DisplayName("should allow requests within limit")
        void shouldAllowWithinLimit() throws ServletException, IOException {
            MockHttpServletRequest request = createRequest("/auth/login", "192.168.1.1");
            MockHttpServletResponse response = new MockHttpServletResponse();

            for (int i = 0; i < 3; i++) {
                filter.doFilter(request, response, filterChain);
            }

            verify(filterChain, org.mockito.Mockito.times(3)).doFilter(request, response);
        }

        @Test
        @DisplayName("should throw TooManyRequestsException after exceeding limit")
        void shouldThrowAfterExceedingLimit() throws ServletException, IOException {
            MockHttpServletRequest request = createRequest("/auth/login", "192.168.1.1");
            MockHttpServletResponse response = new MockHttpServletResponse();

            for (int i = 0; i < 3; i++) {
                filter.doFilter(request, response, filterChain);
            }

            assertThatThrownBy(() -> filter.doFilter(request, response, filterChain))
                .isInstanceOf(TooManyRequestsException.class)
                .hasMessageContaining("Too many requests")
                .satisfies(ex -> {
                    TooManyRequestsException e = (TooManyRequestsException) ex;
                    assertThat(e.getRetryAfterSeconds()).isEqualTo(10);
                });
        }

        @Test
        @DisplayName("should track limits per IP independently")
        void shouldTrackPerIp() throws ServletException, IOException {
            MockHttpServletRequest request1 = createRequest("/auth/login", "192.168.1.1");
            MockHttpServletRequest request2 = createRequest("/auth/login", "192.168.1.2");
            MockHttpServletResponse response = new MockHttpServletResponse();

            for (int i = 0; i < 3; i++) {
                filter.doFilter(request1, response, filterChain);
            }

            filter.doFilter(request2, response, filterChain);

            verify(filterChain, org.mockito.Mockito.times(4)).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        }

        @Test
        @DisplayName("should use X-Forwarded-For header when present")
        void shouldUseXForwardedFor() throws ServletException, IOException {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setServletPath("/auth/login");
            request.addHeader("X-Forwarded-For", "10.0.0.1, 192.168.1.1");

            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
        }
    }

    @Nested
    @DisplayName("register endpoint")
    class RegisterEndpoint {

        @Test
        @DisplayName("should allow requests within limit")
        void shouldAllowWithinLimit() throws ServletException, IOException {
            MockHttpServletRequest request = createRequest("/auth/register", "192.168.1.1");
            MockHttpServletResponse response = new MockHttpServletResponse();

            for (int i = 0; i < 3; i++) {
                filter.doFilter(request, response, filterChain);
            }

            verify(filterChain, org.mockito.Mockito.times(3)).doFilter(request, response);
        }

        @Test
        @DisplayName("should throw after exceeding limit")
        void shouldThrowAfterExceedingLimit() throws ServletException, IOException {
            MockHttpServletRequest request = createRequest("/auth/register", "192.168.1.1");
            MockHttpServletResponse response = new MockHttpServletResponse();

            for (int i = 0; i < 3; i++) {
                filter.doFilter(request, response, filterChain);
            }

            assertThatThrownBy(() -> filter.doFilter(request, response, filterChain))
                .isInstanceOf(TooManyRequestsException.class)
                .satisfies(ex -> {
                    TooManyRequestsException e = (TooManyRequestsException) ex;
                    assertThat(e.getRetryAfterSeconds()).isEqualTo(10);
                });
        }
    }

    @Nested
    @DisplayName("signup endpoint")
    class SignupEndpoint {

        @Test
        @DisplayName("should allow requests within limit")
        void shouldAllowWithinLimit() throws ServletException, IOException {
            MockHttpServletRequest request = createRequest("/auth/signup", "192.168.1.1");
            MockHttpServletResponse response = new MockHttpServletResponse();

            for (int i = 0; i < 3; i++) {
                filter.doFilter(request, response, filterChain);
            }

            verify(filterChain, org.mockito.Mockito.times(3)).doFilter(request, response);
        }

        @Test
        @DisplayName("should throw after exceeding limit")
        void shouldThrowAfterExceedingLimit() throws ServletException, IOException {
            MockHttpServletRequest request = createRequest("/auth/signup", "192.168.1.1");
            MockHttpServletResponse response = new MockHttpServletResponse();

            for (int i = 0; i < 3; i++) {
                filter.doFilter(request, response, filterChain);
            }

            assertThatThrownBy(() -> filter.doFilter(request, response, filterChain))
                .isInstanceOf(TooManyRequestsException.class)
                .satisfies(ex -> {
                    TooManyRequestsException e = (TooManyRequestsException) ex;
                    assertThat(e.getRetryAfterSeconds()).isEqualTo(10);
                });
        }
    }

    @Nested
    @DisplayName("non-auth endpoints")
    class NonAuthEndpoints {

        @Test
        @DisplayName("should not apply rate limiting to non-auth paths")
        void shouldNotApplyToNonAuth() throws ServletException, IOException {
            MockHttpServletRequest request = createRequest("/api/cards", "192.168.1.1");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
        }
    }

    private MockHttpServletRequest createRequest(String path, String ip) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServletPath(path);
        request.setRemoteAddr(ip);
        return request;
    }
}
