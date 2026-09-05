package com.cards.api.mapper;

import com.cards.api.dto.SecurityUser;
import com.cards.api.entity.User;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

@Component
public class SecurityUserMapper {

    public SecurityUser toSecurityUser(User user) {
        if (user == null) return null;

        return new SecurityUser(
            user.getId(),
            user.getUsername(),
            user.getEmail(),
            user.getPasswordHash(),
            user.getZoneInfo(),
            user.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority(role.name())).toList()
        );
    }
}
