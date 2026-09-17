package com.cards.api.repo;

import com.cards.api.entity.PendingRegistration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface PendingRegistrationRepository extends JpaRepository<PendingRegistration, Long> {

    Optional<PendingRegistration> findByTokenHash(String tokenHash);

    @Modifying
    @Query("delete from PendingRegistration p where lower(p.email) = lower(:email)")
    int deleteByEmailIgnoreCase(@Param("email") String email);

    @Modifying
    @Query("delete from PendingRegistration p where lower(p.username) = lower(:username)")
    int deleteByUsernameIgnoreCase(@Param("username") String username);

    @Modifying
    @Query("delete from PendingRegistration p where p.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") Instant cutoff);
}
