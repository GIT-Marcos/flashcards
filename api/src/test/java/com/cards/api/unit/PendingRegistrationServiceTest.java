package com.cards.api.unit;

import com.cards.api.config.properties.ApplicationProperties;
import com.cards.api.entity.PendingRegistration;
import com.cards.api.repo.PendingRegistrationRepository;
import com.cards.api.service.PendingRegistrationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PendingRegistrationService")
class PendingRegistrationServiceTest {

    private static final Instant NOW = Instant.parse("2025-01-01T00:00:00Z");
    private static final long TTL_MS = 86_400_000L;
    private static final String USERNAME = "newuser";
    private static final String EMAIL = "new@email.com";
    private static final String PASSWORD_HASH = "$2a$12$encodedPassword";
    private static final String ZONE = "America/Buenos_Aires";

    @Mock
    private PendingRegistrationRepository repository;

    private PendingRegistrationService service;

    @BeforeEach
    void setUp() {
        var properties = new ApplicationProperties();
        properties.getSecurity().setVerificationTokenExpiration(TTL_MS);

        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

        service = new PendingRegistrationService(repository, properties, clock);
    }

    private String expectedSha256Hex(String raw) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private PendingRegistration pendingWithExpiry(Instant expiresAt) {
        return PendingRegistration.builder()
                .username(USERNAME)
                .email(EMAIL)
                .passwordHash(PASSWORD_HASH)
                .zoneInfo(ZONE)
                .tokenHash(expectedSha256Hex("fixture-token"))
                .expiresAt(expiresAt)
                .build();
    }

    // ======================== CREATE PENDING ========================

    @Nested
    @DisplayName("createPending")
    class CreatePending {

        @Test
        @DisplayName("should return an opaque 43-char Base64URL token")
        void shouldReturnOpaqueBase64UrlToken() {
            String rawToken = service.createPending(USERNAME, EMAIL, PASSWORD_HASH, ZONE);

            assertThat(rawToken).matches("^[A-Za-z0-9_-]{43}$");
        }

        @Test
        @DisplayName("should persist the SHA-256 hex of the token, never the raw token")
        void shouldPersistSha256HexOfToken() {
            String rawToken = service.createPending(USERNAME, EMAIL, PASSWORD_HASH, ZONE);

            ArgumentCaptor<PendingRegistration> captor = ArgumentCaptor.forClass(PendingRegistration.class);
            verify(repository).save(captor.capture());
            PendingRegistration saved = captor.getValue();

            assertThat(saved.getTokenHash()).matches("^[0-9a-f]{64}$");
            assertThat(saved.getTokenHash()).isNotEqualTo(rawToken);
            assertThat(saved.getTokenHash()).isEqualTo(expectedSha256Hex(rawToken));
        }

        @Test
        @DisplayName("should set expiry from verificationTokenExpiration")
        void shouldSetExpiryFromVerificationTokenExpiration() {
            service.createPending(USERNAME, EMAIL, PASSWORD_HASH, ZONE);

            ArgumentCaptor<PendingRegistration> captor = ArgumentCaptor.forClass(PendingRegistration.class);
            verify(repository).save(captor.capture());
            PendingRegistration saved = captor.getValue();

            assertThat(saved.getExpiresAt()).isEqualTo(NOW.plusMillis(TTL_MS));
        }

        @Test
        @DisplayName("should clean up before insert")
        void shouldCleanUpBeforeInsert() {
            service.createPending(USERNAME, EMAIL, PASSWORD_HASH, ZONE);

            InOrder inOrder = inOrder(repository);
            inOrder.verify(repository).deleteExpiredBefore(NOW);
            inOrder.verify(repository).deleteByEmailIgnoreCase(EMAIL);
            inOrder.verify(repository).deleteByUsernameIgnoreCase(USERNAME);
            inOrder.verify(repository).save(any());
        }
    }

    // ======================== FIND VALID BY TOKEN ========================

    @Nested
    @DisplayName("findValidByToken")
    class FindValidByToken {

        @Test
        @DisplayName("should return the row when not expired")
        void shouldReturnRowWhenNotExpired() {
            String rawToken = "opaque-fixture-token";
            when(repository.findByTokenHash(expectedSha256Hex(rawToken)))
                    .thenReturn(Optional.of(pendingWithExpiry(NOW.plusSeconds(3600))));

            Optional<PendingRegistration> result = service.findValidByToken(rawToken);

            assertThat(result).isPresent();
        }

        @Test
        @DisplayName("should return empty when the row is expired")
        void shouldReturnEmptyWhenRowExpired() {
            String rawToken = "opaque-fixture-token";
            when(repository.findByTokenHash(expectedSha256Hex(rawToken)))
                    .thenReturn(Optional.of(pendingWithExpiry(NOW.minusSeconds(1))));

            Optional<PendingRegistration> result = service.findValidByToken(rawToken);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should return empty for an unknown token hash")
        void shouldReturnEmptyForUnknownTokenHash() {
            String rawToken = "unknown-fixture-token";
            when(repository.findByTokenHash(expectedSha256Hex(rawToken)))
                    .thenReturn(Optional.empty());

            Optional<PendingRegistration> result = service.findValidByToken(rawToken);

            assertThat(result).isEmpty();
        }
    }
}
