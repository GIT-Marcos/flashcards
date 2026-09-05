package com.cards.api.dto;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;

public record SecurityUser(
    Long userId,
    String username,
    String email,
    String passwordHash,
    String zoneInfo,
    Collection<? extends GrantedAuthority> authorities

) implements UserDetails {

    @Override
    @NonNull
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public @Nullable String getPassword() {
        return passwordHash;
    }

    @Override
    @NonNull
    public String getUsername() {
        return username;
    }
}
