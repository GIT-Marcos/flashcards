package com.cards.api.repo;

import com.cards.api.entity.CardReviewLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CardReviewLogRepository extends JpaRepository<CardReviewLog, Long> {

    Optional<CardReviewLog> findFirstByUser_IdOrderByCreatedAtDesc(Long userId);

    long countByUserId(Long userId);

    @Query("SELECT AVG(CASE WHEN r.quality >= 3 THEN 1.0 ELSE 0.0 END) FROM CardReviewLog r WHERE r.user.id = :userId")
    Double getAccuracyRateByUserId(@Param("userId") Long userId);

    @Query("SELECT r.quality, COUNT(r) FROM CardReviewLog r WHERE r.user.id = :userId GROUP BY r.quality")
    List<Object[]> getQualityDistributionByUserId(@Param("userId") Long userId);

    @Query("SELECT COUNT(DISTINCT r.card.id) FROM CardReviewLog r WHERE r.user.id = :userId AND r.card IS NOT NULL")
    Long countDistinctCardsReviewedByUserId(@Param("userId") Long userId);
}