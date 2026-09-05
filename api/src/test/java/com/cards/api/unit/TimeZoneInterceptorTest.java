package com.cards.api.unit;

import com.cards.api.dto.SecurityUser;
import com.cards.api.dto.event.UserTimeZoneUpdateEvent;
import com.cards.api.interceptor.TimeZoneInterceptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TimeZoneInterceptor")
class TimeZoneInterceptorTest {

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Captor
    private ArgumentCaptor<UserTimeZoneUpdateEvent> eventCaptor;

    private TimeZoneInterceptor interceptor;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        interceptor = new TimeZoneInterceptor(eventPublisher);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        SecurityContextHolder.clearContext();
    }

    private void authenticateUser() {
        Authentication auth = new TestingAuthenticationToken("testuser", null, "ROLE_USER");
        auth.setAuthenticated(true);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
    }

    @Nested
    @DisplayName("preHandle")
    class PreHandle {

        @Test
        @DisplayName("should publish event when zone is valid")
        void shouldPublishEventForValidZone() throws Exception {
            authenticateUser();
            request.addHeader("Time-Zone", "America/New_York");

            boolean result = interceptor.preHandle(request, response, new Object());

            assertThat(result).isTrue();
            verify(eventPublisher).publishEvent(eventCaptor.capture());
            assertThat(eventCaptor.getValue().zoneInfo()).isEqualTo("America/New_York");
            assertThat(eventCaptor.getValue().username()).isEqualTo("testuser");
        }

        @Test
        @DisplayName("should publish event for UTC offset zone")
        void shouldPublishEventForUtcOffset() throws Exception {
            authenticateUser();
            request.addHeader("Time-Zone", "+01:00");

            boolean result = interceptor.preHandle(request, response, new Object());

            assertThat(result).isTrue();
            verify(eventPublisher).publishEvent(eventCaptor.capture());
            assertThat(eventCaptor.getValue().zoneInfo()).isEqualTo("+01:00");
        }

        @Test
        @DisplayName("should NOT publish event when zone is invalid")
        void shouldNotPublishForInvalidZone() throws Exception {
            authenticateUser();
            request.addHeader("Time-Zone", "Invalid/Zone");

            boolean result = interceptor.preHandle(request, response, new Object());

            assertThat(result).isTrue();
            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        @DisplayName("should NOT publish event when no header")
        void shouldNotPublishWhenNoHeader() throws Exception {
            authenticateUser();

            boolean result = interceptor.preHandle(request, response, new Object());

            assertThat(result).isTrue();
            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        @DisplayName("should NOT publish event when user is anonymous")
        void shouldNotPublishForAnonymousUser() throws Exception {
            Authentication auth = new TestingAuthenticationToken("anonymousUser", null, "ROLE_ANONYMOUS");
            auth.setAuthenticated(true);
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(auth);
            SecurityContextHolder.setContext(context);

            request.addHeader("Time-Zone", "America/New_York");

            boolean result = interceptor.preHandle(request, response, new Object());

            assertThat(result).isTrue();
            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        @DisplayName("should NOT publish event when not authenticated")
        void shouldNotPublishWhenNotAuthenticated() throws Exception {
            request.addHeader("Time-Zone", "America/New_York");

            boolean result = interceptor.preHandle(request, response, new Object());

            assertThat(result).isTrue();
            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        @DisplayName("should NOT publish event when SecurityUser principal has same zone")
        void shouldNotPublishWhenZoneAlreadySet() throws Exception {
            var principal = new SecurityUser(1L, "testuser", "email", "hash", "America/New_York",
                    java.util.List.of(new SimpleGrantedAuthority("ROLE_USER")));
            Authentication auth = new TestingAuthenticationToken(principal, null, "ROLE_USER");
            auth.setAuthenticated(true);
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(auth);
            SecurityContextHolder.setContext(context);

            request.addHeader("Time-Zone", "America/New_York");

            boolean result = interceptor.preHandle(request, response, new Object());

            assertThat(result).isTrue();
            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        @DisplayName("should publish event when SecurityUser principal has different zone")
        void shouldPublishWhenZoneDiffersFromPrincipal() throws Exception {
            var principal = new SecurityUser(1L, "testuser", "email", "hash", "UTC",
                    java.util.List.of(new SimpleGrantedAuthority("ROLE_USER")));
            Authentication auth = new TestingAuthenticationToken(principal, null, "ROLE_USER");
            auth.setAuthenticated(true);
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(auth);
            SecurityContextHolder.setContext(context);

            request.addHeader("Time-Zone", "America/New_York");

            boolean result = interceptor.preHandle(request, response, new Object());

            assertThat(result).isTrue();
            verify(eventPublisher).publishEvent(eventCaptor.capture());
            assertThat(eventCaptor.getValue().zoneInfo()).isEqualTo("America/New_York");
        }

        @Test
        @DisplayName("should always return true")
        void shouldAlwaysReturnTrue() throws Exception {
            boolean result = interceptor.preHandle(request, response, new Object());

            assertThat(result).isTrue();
        }
    }
}
