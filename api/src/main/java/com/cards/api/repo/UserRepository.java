package com.cards.api.repo;

import com.cards.api.dto.DataForNotificationDTO;
import com.cards.api.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    // Actualizar de esta manera en lugar de repo.save() o dirty checking previene errores
    //  si el usuario tiene varios dispositivos abiertos.
    @Modifying
    @Query("UPDATE User u SET u.lastLogin = :now WHERE u.id = :userId")
    int updateLastLogin(@Param("userId") Long userId, @Param("now") Instant now);

    @Query("""
            SELECT DISTINCT new com.cards.api.dto.DataForNotificationDTO(
                u.id, u.username, u.email, u.zoneInfo
            )
            FROM User u
            JOIN u.decks d
            WHERE u.zoneInfo = :zoneInfo
              AND u.notificationsEnabled = TRUE
              AND (u.lastNotificationSent IS NULL OR u.lastNotificationSent < :threshold)
              AND EXISTS (
                  SELECT 1 FROM Card c
                  WHERE c.deck = d
                    AND c.nextReviewDate <= :now
              )
            """)
    List<DataForNotificationDTO> findUsersToNotify(
            @Param("now") Instant now,
            @Param("zoneInfo") String zoneInfo,
            @Param("threshold") Instant threshold
    );

    @Query("SELECT DISTINCT u.zoneInfo FROM User u WHERE u.zoneInfo IS NOT NULL")
    List<String> findDistinctActiveZoneInfos();

    Optional<User> findByUsernameIgnoreCase(String username);

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByEmailIgnoreCase(String email);

    @Query("SELECT COUNT(u) FROM User u WHERE u.lastLogin >= :since")
    long countActiveUsersSince(@Param("since") Instant since);
}
