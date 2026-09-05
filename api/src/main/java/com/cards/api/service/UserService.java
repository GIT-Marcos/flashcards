package com.cards.api.service;

import com.cards.api.dto.request.PatchUserRequest;
import com.cards.api.dto.response.UserResponse;
import com.cards.api.entity.User;
import com.cards.api.exception.ResourceNotFoundException;
import com.cards.api.exception.domain.DuplicatedUserEmailException;
import com.cards.api.exception.domain.DuplicatedUsernameException;
import com.cards.api.mapper.UserMapper;
import com.cards.api.repo.UserRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class UserService {

    private final UserRepository userRepo;
    private final UserMapper mapper;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepo, UserMapper mapper, PasswordEncoder passwordEncoder) {
        this.userRepo = userRepo;
        this.mapper = mapper;
        this.passwordEncoder = passwordEncoder;
    }

    public UserResponse getCurrent(Long authUserId) {
        return userRepo.findById(authUserId)
                .map(mapper::toResponse)
                .orElseThrow(
                        () -> new ResourceNotFoundException("User not found")
                );
    }

    private String extractConstraintName(DataIntegrityViolationException ex) {
        Throwable cause = ex.getCause();
        while (cause != null) {
            if (cause instanceof ConstraintViolationException cve) {
                return cve.getConstraintName();
            }
            cause = cause.getCause();
        }
        return null;
    }

    @Transactional
    public UserResponse patch(Long authUserId, PatchUserRequest request) {
        User managedUser = userRepo.findById(authUserId).orElseThrow(
                () -> new ResourceNotFoundException("User not found")
        );

        if (request.username() != null && !request.username().equals(managedUser.getUsername())) {
            if (userRepo.existsByUsernameIgnoreCase(request.username())) {
                throw new DuplicatedUsernameException(request.username());
            }
        }

        if (request.email() != null && !request.email().equals(managedUser.getEmail())) {
            if (userRepo.existsByEmailIgnoreCase(request.email())) {
                throw new DuplicatedUserEmailException(request.email());
            }
        }

        if (request.password() != null) {
            if (request.currentPassword() == null) {
                throw new BadCredentialsException("The current password is required");
            }

            if (!passwordEncoder.matches(request.currentPassword(), managedUser.getPasswordHash())) {
                throw new BadCredentialsException("The current password does not match");
            }

            managedUser.setPasswordHash(passwordEncoder.encode(request.password()));
        }

        try {
            managedUser = mapper.patchEntity(managedUser, request);
            managedUser = userRepo.save(managedUser);
            return mapper.toResponse(managedUser);
        } catch (DataIntegrityViolationException ex) {
            String constraintName = extractConstraintName(ex);
            if ("uk_users_username_lower".equals(constraintName)) {
                throw new DuplicatedUsernameException(request.username());
            }
            if ("uk_users_email_lower".equals(constraintName)) {
                throw new DuplicatedUserEmailException(request.email());
            }
            throw new BadCredentialsException("Error while saving - Data integrity violation");
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateUserNotificationTimestamp(Long userId, Instant now) {
        userRepo.findById(userId).ifPresent(user -> {
            user.setLastNotificationSent(now);
            userRepo.save(user);
        });
        // Si se lanzara excepción esta se perdería y detendría al hilo
    }

    @Transactional
    public void deleteUser(Long authUserId) {
        if (!userRepo.existsById(authUserId))
            throw new ResourceNotFoundException("User not found");

        userRepo.deleteById(authUserId);
    }
}
