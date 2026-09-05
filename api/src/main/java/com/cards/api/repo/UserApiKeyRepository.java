package com.cards.api.repo;

import com.cards.api.entity.UserApiKey;
import com.cards.api.util.AiProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface UserApiKeyRepository extends JpaRepository<UserApiKey, Long>, JpaSpecificationExecutor<UserApiKey> {

    Optional<UserApiKey> findByIdAndUserId(Long id, Long userId);

    List<UserApiKey> findByUserId(Long userId);

    Optional<UserApiKey> findByUserIdAndProvider(Long userId, AiProvider provider);

    boolean existsByUserIdAndProvider(Long userId, AiProvider provider);
}
