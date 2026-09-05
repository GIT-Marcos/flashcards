package com.cards.api.service;

import com.cards.api.config.properties.ApplicationProperties;
import com.cards.api.dto.SecurityUser;
import com.cards.api.util.TokenType;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Clock;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Service
public class JwtService {

    private final ApplicationProperties properties;
    private final Clock clock;

    public JwtService(ApplicationProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public String generateRefreshToken(SecurityUser securityUser) {
        Map<String, Object> extraClaims = new HashMap<>();
        extraClaims.put("tokenType", TokenType.REFRESH);
        extraClaims.put("userId", securityUser.userId());
        return buildToken(extraClaims, securityUser, properties.getSecurity().getJwt().getRefreshToken().getExpiration());
    }

    private String buildToken(Map<String, Object> extraClaims, UserDetails userDetails, long expiration) {
        Date now = Date.from(clock.instant());

        return Jwts.builder()
            .claims(extraClaims)
            .subject(userDetails.getUsername())
            .issuedAt(now)
            .expiration(new Date(now.getTime() + expiration))
            .signWith(getSignInKey(), Jwts.SIG.HS256)
            .compact();
    }

    public String generateToken(SecurityUser securityUser) {
        Map<String, Object> extraClaims = new HashMap<>();
        extraClaims.put("userId", securityUser.userId());
        extraClaims.put("tokenType", TokenType.ACCESS); // Para evitar que sea validado como el otro tipo de token

        return buildToken(extraClaims, securityUser, properties.getSecurity().getJwt().getExpiration());
    }

    public String generateUnsubscribeToken(Long userId) {
        Map<String, Object> extraClaims = new HashMap<>();
        extraClaims.put("tokenType", TokenType.UNSUBSCRIBE);
        extraClaims.put("userId", userId);

        Date now = Date.from(clock.instant());
        return Jwts.builder()
            .claims(extraClaims)
            .subject("unsubscribe:" + userId)
            .issuedAt(now)
            .expiration(new Date(now.getTime() + properties.getNotifications().getUnsubscribeTokenExpiration()))
            .signWith(getSignInKey(), Jwts.SIG.HS256)
            .compact();
    }

    public String generateEmailVerificationToken(String username, String email, String passwordHash, String zoneInfo) {
        Map<String, Object> extraClaims = new HashMap<>();
        extraClaims.put("tokenType", TokenType.VERIFY_EMAIL);
        extraClaims.put("email", email);
        extraClaims.put("passwordHash", passwordHash);
        extraClaims.put("zoneInfo", zoneInfo);

        Date now = Date.from(clock.instant());
        return Jwts.builder()
            .claims(extraClaims)
            .subject(username)
            .issuedAt(now)
            .expiration(new Date(now.getTime() + properties.getSecurity().getVerificationTokenExpiration()))
            .signWith(getSignInKey(), Jwts.SIG.HS256)
            .compact();
    }

    public String generatePasswordResetToken(Long userId, String email, String passwordHash) {
        Map<String, Object> extraClaims = new HashMap<>();
        extraClaims.put("tokenType", TokenType.PASSWORD_RESET);
        extraClaims.put("email", email);
        extraClaims.put("passwordHash", passwordHash);

        Date now = Date.from(clock.instant());
        return Jwts.builder()
            .claims(extraClaims)
            .subject(String.valueOf(userId))
            .issuedAt(now)
            .expiration(new Date(now.getTime() + properties.getSecurity().getResetTokenExpiration()))
            .signWith(getSignInKey(), Jwts.SIG.HS256)
            .compact();
    }

    public record VerifyData(String username, String email, String passwordHash, String zoneInfo) {
    }

    public VerifyData extractVerificationData(String token) {
        Claims claims = extractAllClaims(token);
        return new VerifyData(
            claims.getSubject(),
            claims.get("email", String.class),
            claims.get("passwordHash", String.class),
            claims.get("zoneInfo", String.class)
        );
    }

    public record ResetPasswordData(Long userId, String email, String passwordHash) {
    }

    public ResetPasswordData extractResetPasswordData(String token) {
        Claims claims = extractAllClaims(token);
        return new ResetPasswordData(
            Long.valueOf(claims.getSubject()),
            claims.get("email", String.class),
            claims.get("passwordHash", String.class)
        );
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        final Long tokenUserId = extractUserId(token);
        final SecurityUser securityUser = (SecurityUser) userDetails;

        return (tokenUserId.equals(securityUser.userId())) && !isTokenExpired(token);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public Long extractUserId(String token) {
        return extractClaim(token, claims -> claims.get("userId", Long.class));
    }

    public Claims extractAllClaims(String token) {
        return buildParser()
            .parseSignedClaims(token)
            .getPayload();
    }

    public boolean isTokenType(String token, String expectedType) {
        try {
            Claims claims = extractAllClaims(token);
            return expectedType.equals(claims.get("tokenType", String.class));
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isRefreshToken(String token) {
        return isTokenType(token, TokenType.REFRESH);
    }

    public boolean isResetPasswordToken(String token) {
        return isTokenType(token, TokenType.PASSWORD_RESET);
    }

    private JwtParser buildParser() {
        return Jwts.parser()
            .clock(() -> Date.from(clock.instant()))
            .verifyWith(getSignInKey())
            .build();
    }

    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(Date.from(clock.instant()));
    }

    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    private SecretKey getSignInKey() {
        byte[] keyBytes = Decoders.BASE64.decode(properties.getSecurity().getJwt().getSecretKey());
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
