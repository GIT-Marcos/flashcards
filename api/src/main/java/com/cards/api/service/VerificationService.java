package com.cards.api.service;

import com.cards.api.entity.PendingRegistration;
import com.cards.api.entity.User;
import com.cards.api.exception.domain.DuplicatedUserEmailException;
import com.cards.api.exception.domain.DuplicatedUsernameException;
import com.cards.api.exception.domain.InvalidEmailVerificationException;
import com.cards.api.repo.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Set;

@Service
public class VerificationService {

    private static final Logger log = LoggerFactory.getLogger(VerificationService.class);

    private final PendingRegistrationService pendingRegistrationService;
    private final UserRepository userRepository;

    public VerificationService(PendingRegistrationService pendingRegistrationService, UserRepository userRepository) {
        this.pendingRegistrationService = pendingRegistrationService;
        this.userRepository = userRepository;
    }

    public void validateTokenStructure(String token) {
        pendingRegistrationService.findValidByToken(token)
                .orElseThrow(() -> new InvalidEmailVerificationException(
                        "This verification link is invalid or has expired. Please sign up again."));
    }

    @Transactional
    public void confirmEmail(String token) {
        PendingRegistration pending = pendingRegistrationService.findValidByToken(token)
                .orElseThrow(() -> new InvalidEmailVerificationException(
                        "This verification link is invalid or has expired. Please sign up again."));

        if (userRepository.existsByUsernameIgnoreCase(pending.getUsername()))
            throw new DuplicatedUsernameException(pending.getUsername());

        if (userRepository.existsByEmailIgnoreCase(pending.getEmail().toLowerCase(Locale.ROOT)))
            throw new DuplicatedUserEmailException(pending.getEmail());

        User user = User.builder()
                .username(pending.getUsername())
                .email(pending.getEmail())
                .passwordHash(pending.getPasswordHash())
                .roles(Set.of(User.UserRole.ROLE_USER))
                .zoneInfo(pending.getZoneInfo())
                .build();

        try {
            userRepository.save(user);
            pendingRegistrationService.delete(pending);
        } catch (Exception ex) {
            log.warn("Failed to create user from pending registration: {}", ex.getMessage(), ex);
            throw new InvalidEmailVerificationException(
                    "This verification link is invalid or has expired. Please sign up again.");
        }
    }
}
