package com.cards.api.mapper;

import com.cards.api.dto.request.PatchUserRequest;
import com.cards.api.dto.request.RegisterRequest;
import com.cards.api.dto.response.UserResponse;
import com.cards.api.entity.User;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

@Component
public class UserMapper {

    public User toEntity(RegisterRequest request, String passHash) {
        if (request == null) return null;

        return User.builder()
            .username(request.username())
            .email(request.email())
            .passwordHash(passHash)
            .roles(Set.of(User.UserRole.ROLE_USER))
            .zoneInfo(request.zoneInfo())
            .build();
    }

    public User patchEntity(User userToPatch, PatchUserRequest request) {
        if (request == null || userToPatch == null) return null;

        if (request.username() != null)
            userToPatch.setUsername(request.username());

        if (request.email() != null)
            userToPatch.setEmail(request.email());

        if (request.sessionThreshold() != null)
            userToPatch.setSessionThreshold(request.sessionThreshold());

        if (request.startOfDay() != null)
            userToPatch.setStartOfDay(request.startOfDay());

        if (request.notificationsEnabled() != null)
            userToPatch.setNotificationsEnabled(request.notificationsEnabled());

        return userToPatch;
    }

    public UserResponse toResponse(User user) {
        if (user == null) return null;

        return new UserResponse(
            user.getId(),
            user.getUsername(),
            user.getEmail(),
            user.getZoneInfo(),
            user.getCreatedAt(),
            user.getLastLogin(),
            user.getLastNotificationSent(),
            user.getSessionThreshold(),
            user.getStartOfDay(),
            user.isNotificationsEnabled(),
            user.getRoles().stream().map(Enum::toString).collect(Collectors.toSet())
        );
    }
}
