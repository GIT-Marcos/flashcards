package com.cards.api.unit;

import com.cards.api.config.properties.ApplicationProperties;
import com.cards.api.dto.SecurityUser;
import com.cards.api.service.JwtService;
import com.cards.api.util.TokenType;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
@DisplayName("JwtService")
class JwtServiceTest {

    private JwtService jwtService;
    private final Clock clock = Clock.fixed(
        Instant.parse("2025-01-01T00:00:00Z"),
        ZoneOffset.UTC
    );

    private static final long JWT_EXPIRATION_MS = 900_000L;       // 15 min
    private static final long REFRESH_EXPIRATION_MS = 6_048_000_00L; // 7 days

    private SecurityUser securityUser;

    @BeforeEach
    void setUp() {

        byte[] keyBytes = new byte[32];
        Arrays.fill(keyBytes, (byte) 'a');
        String base64Key = Base64.getEncoder().encodeToString(keyBytes);

        var properties = new ApplicationProperties();
        properties.getSecurity().getJwt().setSecretKey(base64Key);
        properties.getSecurity().getJwt().setExpiration(JWT_EXPIRATION_MS);
        properties.getSecurity().getJwt().getRefreshToken().setExpiration(REFRESH_EXPIRATION_MS);

        jwtService = new JwtService(properties, clock);

        Collection<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_USER"));
        securityUser = new SecurityUser(1L, "testuser", "test@email.com", "hashedPwd", "America/Buenos_Aires", authorities);
    }

    // ======================== TOKEN GENERATION ========================

    @Nested
    @DisplayName("generateToken")
    class GenerateToken {

        @Test
        @DisplayName("should contain ACCESS token type in claims")
        void shouldContainAccessType() {
            String token = jwtService.generateToken(securityUser);
            Claims claims = jwtService.extractAllClaims(token);

            assertThat(claims.get("tokenType", String.class)).isEqualTo(TokenType.ACCESS);
        }

        @Test
        @DisplayName("should contain userId in claims")
        void shouldContainUserId() {
            String token = jwtService.generateToken(securityUser);
            Claims claims = jwtService.extractAllClaims(token);

            assertThat(claims.get("userId", Long.class)).isEqualTo(1L);
        }

        @Test
        @DisplayName("should set subject equal to username")
        void shouldSetSubjectToUsername() {
            String token = jwtService.generateToken(securityUser);

            assertThat(jwtService.extractUsername(token)).isEqualTo("testuser");
        }

        @Test
        @DisplayName("should set issuedAt and future expiration")
        void shouldSetTimestamps() {
            String token = jwtService.generateToken(securityUser);
            Claims claims = jwtService.extractAllClaims(token);

            assertThat(claims.getIssuedAt()).isNotNull();
            assertThat(claims.getExpiration()).isAfter(claims.getIssuedAt());
        }
    }

    @Nested
    @DisplayName("generateRefreshToken")
    class GenerateRefreshToken {

        @Test
        @DisplayName("should contain REFRESH token type in claims")
        void shouldContainRefreshType() {
            String token = jwtService.generateRefreshToken(securityUser);
            Claims claims = jwtService.extractAllClaims(token);

            assertThat(claims.get("tokenType", String.class)).isEqualTo(TokenType.REFRESH);
        }

        @Test
        @DisplayName("should set subject equal to username")
        void shouldSetSubjectToUsername() {
            String token = jwtService.generateRefreshToken(securityUser);

            assertThat(jwtService.extractUsername(token)).isEqualTo("testuser");
        }

        @Test
        @DisplayName("should contain userId claim")
        void shouldContainUserId() {
            String token = jwtService.generateRefreshToken(securityUser);
            Claims claims = jwtService.extractAllClaims(token);

            assertThat(claims.get("userId", Long.class)).isEqualTo(1L);
        }
    }

    // ======================== EXTRACTION ========================

    @Nested
    @DisplayName("extractClaim")
    class ExtractClaim {

        @Test
        @DisplayName("should extract userId from access token")
        void shouldExtractUserId() {
            String token = jwtService.generateToken(securityUser);

            Long userId = jwtService.extractUserId(token);

            assertThat(userId).isEqualTo(1L);
        }

        @Test
        @DisplayName("should extract username (subject)")
        void shouldExtractUsername() {
            String token = jwtService.generateToken(securityUser);

            String username = jwtService.extractUsername(token);

            assertThat(username).isEqualTo("testuser");
        }

        @Test
        @DisplayName("should extract all claims")
        void shouldExtractAllClaims() {
            String token = jwtService.generateToken(securityUser);
            Claims claims = jwtService.extractAllClaims(token);

            assertThat(claims.getSubject()).isEqualTo("testuser");
            assertThat(claims.get("tokenType", String.class)).isEqualTo(TokenType.ACCESS);
        }

        @Test
        @DisplayName("should extract arbitrary claim via generic extractClaim")
        void shouldExtractArbitraryClaim() {
            String token = jwtService.generateToken(securityUser);

            String tokenType = jwtService.extractClaim(token, c -> c.get("tokenType", String.class));

            assertThat(tokenType).isEqualTo(TokenType.ACCESS);
        }
    }

    // ======================== VALIDATION ========================

    @Nested
    @DisplayName("isTokenValid")
    class IsTokenValid {

        @Test
        @DisplayName("should return true for valid token belonging to same user")
        void shouldReturnTrueForMatchingUser() {
            String token = jwtService.generateToken(securityUser);

            boolean valid = jwtService.isTokenValid(token, securityUser);

            assertThat(valid).isTrue();
        }

        @Test
        @DisplayName("should return false for valid token belonging to different user")
        void shouldReturnFalseForDifferentUser() {
            String token = jwtService.generateToken(securityUser);

            Collection<GrantedAuthority> otherAuthorities = List.of(new SimpleGrantedAuthority("ROLE_USER"));
            SecurityUser otherUser = new SecurityUser(99L, "other", "other@email.com", "hash", "UTC", otherAuthorities);

            assertThat(jwtService.isTokenValid(token, otherUser)).isFalse();
        }

        @Test
        @DisplayName("should throw ExpiredJwtException when token has expired")
        void shouldThrowWhenExpired() {
            var props = new ApplicationProperties();
            props.getSecurity().getJwt().setSecretKey(
                Base64.getEncoder().encodeToString(new byte[32])
            );
            props.getSecurity().getJwt().setExpiration(-1_000L);
            var svc = new JwtService(props, clock);

            String token = svc.generateToken(securityUser);

            assertThatThrownBy(() -> svc.isTokenValid(token, securityUser))
                .isInstanceOf(ExpiredJwtException.class);
        }
    }
}
