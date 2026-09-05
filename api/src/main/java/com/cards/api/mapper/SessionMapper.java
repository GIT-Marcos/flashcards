package com.cards.api.mapper;

import com.cards.api.dto.response.SessionResponse;
import com.cards.api.entity.StudySession;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class SessionMapper {

    public SessionResponse toResponse(StudySession session) {
        if (session == null) return null;

        long durationSeconds = 0L;
        if (session.getStartTime() != null && session.getEndTime() != null) {
            durationSeconds = Math.max(0L,
                Duration.between(session.getStartTime(), session.getEndTime()).toSeconds());
        }

        return new SessionResponse(
            session.getId(),
            session.getStartTime(),
            session.getEndTime(),
            session.getCardsReviewed(),
            session.getAccuracyRate(),
            durationSeconds
        );
    }
}
