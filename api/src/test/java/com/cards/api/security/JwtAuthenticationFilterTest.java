package com.cards.api.security;

import com.cards.api.dto.SecurityUser;
import com.cards.api.service.CustomUserDetailService;
import com.cards.api.service.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("JwtAuthenticationFilter")
class JwtAuthenticationFilterTest {

    @Mock
    private JwtService jwtService;
    @Mock
    private CustomUserDetailService customUserDetailService;
    @Mock
    private org.springframework.web.servlet.HandlerExceptionResolver handlerExceptionResolver;
    @Mock
    private MockFilterChain filterChain;

    @Captor
    ArgumentCaptor<SecurityUser> userCaptor;

    private JwtAuthenticationFilter filter;

    private static final String VALID_TOKEN = "valid.jwt.token";
    private static final Long USER_ID = 1L;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(jwtService, customUserDetailService, handlerExceptionResolver);
        SecurityContextHolder.clearContext();
    }

    private SecurityUser createSecurityUser() {
        Collection<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_USER"));
        return new SecurityUser(USER_ID, "testuser", "test@email.com", "hashed", "UTC", authorities);
    }

    // ======================== VALID TOKEN ========================

    @Nested
    @DisplayName("Valid JWT")
    class ValidJwt {

        @Test
        @DisplayName("should set authentication in SecurityContext when token is valid")
        void shouldSetAuthentication() throws Exception {
            SecurityUser securityUser = createSecurityUser();
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("Authorization", "Bearer " + VALID_TOKEN);
            MockHttpServletResponse response = new MockHttpServletResponse();

            when(jwtService.extractUserId(VALID_TOKEN)).thenReturn(USER_ID);
            when(customUserDetailService.loadByUserId(USER_ID)).thenReturn(securityUser);
            when(jwtService.isTokenValid(eq(VALID_TOKEN), any(SecurityUser.class))).thenReturn(true);

            filter.doFilterInternal(request, response, filterChain);

            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
            assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo("testuser");
            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("should NOT override existing authentication when already set")
        void shouldNotOverrideExistingAuth() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("Authorization", "Bearer " + VALID_TOKEN);
            MockHttpServletResponse response = new MockHttpServletResponse();

            // Simulate already authenticated
            org.springframework.security.authentication.UsernamePasswordAuthenticationToken existingAuth =
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                    createSecurityUser(), null, createSecurityUser().getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(existingAuth);

            filter.doFilterInternal(request, response, filterChain);

            // Should still be the same auth, filter should skip
            verify(jwtService, never()).isTokenValid(anyString(), any());
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isEqualTo(existingAuth);
        }
    }

    // ======================== NO AUTH HEADER ========================

    @Nested
    @DisplayName("No Authorization header")
    class NoAuthHeader {

        @Test
        @DisplayName("should pass through filter chain when no Authorization header")
        void shouldPassThroughWhenNoHeader() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest();
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, filterChain);

            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
            verify(filterChain).doFilter(request, response);
            verifyNoInteractions(jwtService);
        }

        @Test
        @DisplayName("should pass through when Authorization header does not start with Bearer")
        void shouldPassThroughWhenNotBearer() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("Authorization", "Basic dXNlcjpwYXNz");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, filterChain);

            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
            verify(filterChain).doFilter(request, response);
            verifyNoInteractions(jwtService);
        }

        @Test
        @DisplayName("should pass through when Authorization header has only 'Bearer ' without token")
        void shouldPassThroughWhenBearerOnly() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("Authorization", "Bearer ");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
        }
    }

    // ======================== INVALID TOKEN ========================

    @Nested
    @DisplayName("Invalid JWT")
    class InvalidJwt {

        @Test
        @DisplayName("should delegate exception to HandlerExceptionResolver on invalid token")
        void shouldDelegateException() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("Authorization", "Bearer " + VALID_TOKEN);
            MockHttpServletResponse response = new MockHttpServletResponse();

            when(jwtService.extractUserId(VALID_TOKEN))
                .thenThrow(new io.jsonwebtoken.JwtException("Invalid token"));

            filter.doFilterInternal(request, response, filterChain);

            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
            verify(handlerExceptionResolver).resolveException(eq(request), eq(response), eq(null), any(io.jsonwebtoken.JwtException.class));
            verify(filterChain, never()).doFilter(any(), any());
        }

        @Test
        @DisplayName("should delegate expired token exception to resolver")
        void shouldDelegateExpiredException() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("Authorization", "Bearer expired.token");
            MockHttpServletResponse response = new MockHttpServletResponse();

            when(jwtService.extractUserId("expired.token"))
                .thenThrow(new io.jsonwebtoken.ExpiredJwtException(null, null, "Token expired"));

            filter.doFilterInternal(request, response, filterChain);

            verify(handlerExceptionResolver).resolveException(eq(request), eq(response), eq(null), any(io.jsonwebtoken.ExpiredJwtException.class));
        }

        @Test
        @DisplayName("should delegate signature exception to resolver")
        void shouldDelegateSignatureException() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("Authorization", "Bearer tampered.token");
            MockHttpServletResponse response = new MockHttpServletResponse();

            when(jwtService.extractUserId("tampered.token"))
                .thenThrow(new io.jsonwebtoken.security.SignatureException("Invalid signature"));

            filter.doFilterInternal(request, response, filterChain);

            verify(handlerExceptionResolver).resolveException(eq(request), eq(response), eq(null), any(io.jsonwebtoken.security.SignatureException.class));
        }
    }

    // ======================== USER NOT FOUND ========================

    @Nested
    @DisplayName("User resolution")
    class UserResolution {

        @Test
        @DisplayName("should delegate exception when user not found for userId in token")
        void shouldDelegateWhenUserNotFound() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("Authorization", "Bearer " + VALID_TOKEN);
            MockHttpServletResponse response = new MockHttpServletResponse();

            when(jwtService.extractUserId(VALID_TOKEN)).thenReturn(USER_ID);
            when(customUserDetailService.loadByUserId(USER_ID))
                .thenThrow(new com.cards.api.exception.ResourceNotFoundException("User not found"));

            filter.doFilterInternal(request, response, filterChain);

            verify(handlerExceptionResolver).resolveException(eq(request), eq(response), eq(null),
                any(com.cards.api.exception.ResourceNotFoundException.class));
        }
    }
}
