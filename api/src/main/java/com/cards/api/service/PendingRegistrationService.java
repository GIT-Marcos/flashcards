package com.cards.api.service;

import com.cards.api.config.properties.ApplicationProperties;
import com.cards.api.entity.PendingRegistration;
import com.cards.api.repo.PendingRegistrationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

@Service
@Transactional
public class PendingRegistrationService {

    private static final Logger log = LoggerFactory.getLogger(PendingRegistrationService.class);

    private final PendingRegistrationRepository repository;
    private final ApplicationProperties properties;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    public PendingRegistrationService(PendingRegistrationRepository repository,
                                      ApplicationProperties properties,
                                      Clock clock) {
        this.repository = repository;
        this.properties = properties;
        this.clock = clock;
    }

    public String createPending(String username, String email, String passwordHash, String zoneInfo) {
        Instant now = clock.instant();

        repository.deleteExpiredBefore(now);
        repository.deleteByEmailIgnoreCase(email);
        repository.deleteByUsernameIgnoreCase(username);

        byte[] tokenBytes = new byte[32];
        secureRandom.nextBytes(tokenBytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);

        String tokenHash = sha256Hex(rawToken);

        Instant expiresAt = now.plusMillis(properties.getSecurity().getVerificationTokenExpiration());

        PendingRegistration pending = PendingRegistration.builder()
                .username(username)
                .email(email)
                .passwordHash(passwordHash)
                .zoneInfo(zoneInfo)
                .tokenHash(tokenHash)
                .expiresAt(expiresAt)
                .build();

        repository.save(pending);
        return rawToken;
    }

    public Optional<PendingRegistration> findValidByToken(String rawToken) {
        String tokenHash = sha256Hex(rawToken);
        return repository.findByTokenHash(tokenHash)
                .filter(p -> p.getExpiresAt().isAfter(clock.instant()));
    }

    public void delete(PendingRegistration pending) {
        repository.delete(pending);
    }

    private String sha256Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
