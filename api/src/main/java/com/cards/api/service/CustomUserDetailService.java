package com.cards.api.service;

import com.cards.api.dto.SecurityUser;
import com.cards.api.exception.ResourceNotFoundException;
import com.cards.api.mapper.SecurityUserMapper;
import com.cards.api.repo.UserRepository;
import org.jspecify.annotations.NonNull;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class CustomUserDetailService implements UserDetailsService {

    private final UserRepository userRepo;
    private final SecurityUserMapper securityUserMapper;

    public CustomUserDetailService(UserRepository userRepo, SecurityUserMapper securityUserMapper) {
        this.userRepo = userRepo;
        this.securityUserMapper = securityUserMapper;
    }

    @Override
    @NonNull
    public UserDetails loadUserByUsername(@NonNull String username) throws UsernameNotFoundException {
        return userRepo.findByUsernameIgnoreCase(username)
            .map(securityUserMapper::toSecurityUser)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    public SecurityUser loadByUserId(Long userId) throws ResourceNotFoundException {
        return userRepo.findById(userId)
            .map(securityUserMapper::toSecurityUser)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }
}
