package com.cards.api.service;

import com.cards.api.dto.response.UserStatsResponse;
import com.cards.api.repo.CardReviewLogRepository;
import com.cards.api.repo.StudySessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class StatsService {

    private final CardReviewLogRepository cardReviewLogRepo;
    private final StudySessionRepository studySessionRepo;

    public StatsService(CardReviewLogRepository cardReviewLogRepo, StudySessionRepository studySessionRepo) {
        this.cardReviewLogRepo = cardReviewLogRepo;
        this.studySessionRepo = studySessionRepo;
    }

    public UserStatsResponse getUserStats(Long userId) {
        Long totalReviews = cardReviewLogRepo.countByUserId(userId);
        Double accuracyRate = cardReviewLogRepo.getAccuracyRateByUserId(userId);
        Long totalSessions = studySessionRepo.countByUserId(userId);
        Long totalCardsReviewed = cardReviewLogRepo.countDistinctCardsReviewedByUserId(userId);
        Map<Integer, Long> qualityDistribution = buildQualityDistribution(userId);

        return new UserStatsResponse(
                totalReviews,
                accuracyRate != null ? accuracyRate : 0.0,
                totalSessions,
                totalCardsReviewed,
                qualityDistribution
        );
    }

    private Map<Integer, Long> buildQualityDistribution(Long userId) {
        List<Object[]> distribution = cardReviewLogRepo.getQualityDistributionByUserId(userId);

        return distribution.stream()
                .collect(Collectors.toMap(
                        row -> (Integer) row[0],
                        row -> (Long) row[1],
                        (a, b) -> a,
                        LinkedHashMap::new
                ));
    }
}