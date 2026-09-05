export interface SessionResponse {
    id: number;
    startTime: string;
    endTime: string;
    cardsReviewed: number;
    accuracyRate: number;
    durationSeconds: number;
}

export interface UserStatsResponse {
    totalReviews: number;
    globalAccuracyRate: number;
    totalSessions: number;
    totalCardsReviewed: number;
    qualityDistribution: Record<number, number>;
}
