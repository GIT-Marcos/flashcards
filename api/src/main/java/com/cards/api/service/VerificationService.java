package com.cards.api.service;

import com.cards.api.entity.User;
import com.cards.api.exception.domain.DuplicatedUserEmailException;
import com.cards.api.exception.domain.DuplicatedUsernameException;
import com.cards.api.exception.domain.InvalidEmailVerificationException;
import com.cards.api.repo.UserRepository;
import com.cards.api.util.TokenType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Set;

@Service
public class VerificationService {

    private static final Logger log = LoggerFactory.getLogger(VerificationService.class);

    private final JwtService jwtService;
    private final UserRepository userRepository;

    public VerificationService(JwtService jwtService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    public void validateTokenStructure(String token) {
        jwtService.extractAllClaims(token);
    }

    @Transactional
    public void confirmEmail(String token) {
        JwtService.VerifyData data;
        try {
            data = jwtService.extractVerificationData(token);
        } catch (Exception ex) {
            throw new InvalidEmailVerificationException(
                "This verification link is invalid or has expired. Please sign up again.");
        }

        if (!jwtService.isTokenType(token, TokenType.VERIFY_EMAIL)) {
            throw new InvalidEmailVerificationException(
                "This verification link is invalid or has expired. Please sign up again.");
        }

        if (userRepository.existsByUsernameIgnoreCase(data.username()))
            throw new DuplicatedUsernameException(data.username());

        if (userRepository.existsByEmailIgnoreCase(data.email().toLowerCase(Locale.ROOT)))
            throw new DuplicatedUserEmailException(data.email());

        User user = User.builder()
            .username(data.username())
            .email(data.email())
            .passwordHash(data.passwordHash())
            .roles(Set.of(User.UserRole.ROLE_USER))
            .zoneInfo(data.zoneInfo())
            .build();

        try {
            userRepository.save(user);
        } catch (Exception ex) {
            log.warn("Failed to create user from verification token: {}", ex.getMessage(), ex);
            throw new InvalidEmailVerificationException(
                "This verification link is invalid or has expired. Please sign up again.");
        }
    }
}
