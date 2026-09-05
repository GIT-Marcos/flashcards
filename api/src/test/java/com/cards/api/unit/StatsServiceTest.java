package com.cards.api.unit;

import com.cards.api.dto.response.UserStatsResponse;
import com.cards.api.repo.CardReviewLogRepository;
import com.cards.api.repo.StudySessionRepository;
import com.cards.api.service.StatsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("StatsService")
class StatsServiceTest {

    @Mock
    private CardReviewLogRepository cardReviewLogRepo;
    @Mock
    private StudySessionRepository studySessionRepo;

    private StatsService statsService;

    private static final Long USER_ID = 1L;

    @BeforeEach
    void setUp() {
        statsService = new StatsService(cardReviewLogRepo, studySessionRepo);
    }

    @Nested
    @DisplayName("getUserStats")
    class GetUserStats {

        @Test
        @DisplayName("should return complete stats when user has reviews")
        void shouldReturnCompleteStats() {
            // Arrange
            when(cardReviewLogRepo.countByUserId(USER_ID)).thenReturn(10L);
            when(cardReviewLogRepo.getAccuracyRateByUserId(USER_ID)).thenReturn(0.75);
            when(studySessionRepo.countByUserId(USER_ID)).thenReturn(3L);
            when(cardReviewLogRepo.countDistinctCardsReviewedByUserId(USER_ID)).thenReturn(8L);
            when(cardReviewLogRepo.getQualityDistributionByUserId(USER_ID)).thenReturn(List.of(
                    new Object[]{3, 2L},
                    new Object[]{4, 5L},
                    new Object[]{5, 3L}
            ));

            // Act
            UserStatsResponse result = statsService.getUserStats(USER_ID);

            // Assert
            assertThat(result.totalReviews()).isEqualTo(10L);
            assertThat(result.globalAccuracyRate()).isEqualTo(0.75);
            assertThat(result.totalSessions()).isEqualTo(3L);
            assertThat(result.totalCardsReviewed()).isEqualTo(8L);
            assertThat(result.qualityDistribution()).hasSize(3);
            assertThat(result.qualityDistribution().get(3)).isEqualTo(2L);
            assertThat(result.qualityDistribution().get(4)).isEqualTo(5L);
            assertThat(result.qualityDistribution().get(5)).isEqualTo(3L);
        }

        @Test
        @DisplayName("should return zero accuracy when user has no reviews")
        void shouldReturnZeroAccuracyWhenNoReviews() {
            // Arrange
            when(cardReviewLogRepo.countByUserId(USER_ID)).thenReturn(0L);
            when(cardReviewLogRepo.getAccuracyRateByUserId(USER_ID)).thenReturn(null);
            when(studySessionRepo.countByUserId(USER_ID)).thenReturn(0L);
            when(cardReviewLogRepo.countDistinctCardsReviewedByUserId(USER_ID)).thenReturn(0L);
            when(cardReviewLogRepo.getQualityDistributionByUserId(USER_ID)).thenReturn(List.of());

            // Act
            UserStatsResponse result = statsService.getUserStats(USER_ID);

            // Assert
            assertThat(result.totalReviews()).isZero();
            assertThat(result.globalAccuracyRate()).isZero();
            assertThat(result.totalSessions()).isZero();
            assertThat(result.totalCardsReviewed()).isZero();
            assertThat(result.qualityDistribution()).isEmpty();
        }
    }
}