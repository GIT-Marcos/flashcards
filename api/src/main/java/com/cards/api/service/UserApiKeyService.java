package com.cards.api.service;

import com.cards.api.dto.request.CreateApiKeyRequest;
import com.cards.api.dto.response.ApiKeyResponse;
import com.cards.api.entity.User;
import com.cards.api.entity.UserApiKey;
import com.cards.api.exception.ResourceNotFoundException;
import com.cards.api.exception.domain.DuplicatedApiKeyException;
import com.cards.api.mapper.UserApiKeyMapper;
import com.cards.api.repo.UserApiKeyRepository;
import com.cards.api.repo.UserRepository;
import com.cards.api.util.AiProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UserApiKeyService {

    private final UserApiKeyRepository repository;
    private final UserRepository userRepo;
    private final UserApiKeyMapper mapper;

    public UserApiKeyService(UserApiKeyRepository repository, UserRepository userRepo, UserApiKeyMapper mapper) {
        this.repository = repository;
        this.userRepo = userRepo;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<ApiKeyResponse> getUserKeys(Long userId) {
        return repository.findByUserId(userId).stream()
            .map(mapper::toResponse)
            .toList();
    }

    @Transactional
    public ApiKeyResponse createKey(Long userId, CreateApiKeyRequest request) {
        if (repository.existsByUserIdAndProvider(userId, request.provider())) {
            throw new DuplicatedApiKeyException(request.provider().name());
        }

        User owner = userRepo.getReferenceById(userId);

        try {
            UserApiKey entity = mapper.toEntity(owner, request);
            entity = repository.save(entity);
            return mapper.toResponse(entity);
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicatedApiKeyException(request.provider().name());
        }
    }

    @Transactional
    public void deleteKey(Long userId, Long keyId) {
        UserApiKey key = repository.findByIdAndUserId(keyId, userId)
            .orElseThrow(() -> new ResourceNotFoundException("API key not found"));
        repository.delete(key);
    }

    @Transactional(readOnly = true)
    public String getDecryptedKey(Long userId, AiProvider provider) {
        UserApiKey key = repository.findByUserIdAndProvider(userId, provider)
            .orElseThrow(() -> new ResourceNotFoundException("No API key found for " + provider));
        return key.getEncryptedKey();
    }

    @Transactional(readOnly = true)
    public List<ApiKeyResponse> getAdminUserKeys(Long targetUserId) {
        return repository.findByUserId(targetUserId).stream()
            .map(mapper::toResponse)
            .toList();
    }

    @Transactional
    public void adminDeleteKey(Long keyId) {
        UserApiKey key = repository.findById(keyId)
            .orElseThrow(() -> new ResourceNotFoundException("API key not found"));
        repository.delete(key);
    }
}
