package com.cards.api.service;

import com.cards.api.dto.request.CursorPaginationRequest;
import com.cards.api.dto.response.SessionResponse;
import com.cards.api.entity.CardReviewLog;
import com.cards.api.entity.StudySession;
import com.cards.api.entity.User;
import com.cards.api.exception.ResourceNotFoundException;
import com.cards.api.mapper.SessionMapper;
import com.cards.api.repo.CardReviewLogRepository;
import com.cards.api.repo.StudySessionRepository;
import com.cards.api.repo.UserRepository;
import com.cards.api.specification.StudySessionSpecifications;
import com.cards.api.util.TimeZoneUtils;
import org.springframework.data.domain.Window;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;

@Service
public class SessionService {

    private final StudySessionRepository studySessionRepo;
    private final CardReviewLogRepository logRepo;
    private final UserRepository userRepo;
    private final SessionMapper sessionMapper;
    private final Clock clock;

    public SessionService(StudySessionRepository studySessionRepo, CardReviewLogRepository logRepo, UserRepository userRepo, SessionMapper sessionMapper, Clock clock) {
        this.studySessionRepo = studySessionRepo;
        this.logRepo = logRepo;
        this.userRepo = userRepo;
        this.sessionMapper = sessionMapper;
        this.clock = clock;
    }

    @Transactional
    public StudySession getOrCreateActiveSession(Long userId) {
        return getOrCreateActiveSessionInternal(userId);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void updateMetrics(Long sessionId, int quality, Instant now) {
        StudySession session = studySessionRepo.findById(sessionId)
            .orElseThrow(() -> new ResourceNotFoundException("StudySession not found"));
        session.updateMetrics(quality, now);
    }

    private StudySession getOrCreateActiveSessionInternal(Long userId) {
        User user = userRepo.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found"));

        return logRepo.findFirstByUser_IdOrderByCreatedAtDesc(userId)
            .map(lastLog -> {
                if (isSessionActive(lastLog, user)) {
                    return lastLog.getStudySession();
                }

                return createNewSession(user);
            })
            .orElseGet(() -> createNewSession(user));
    }

    public Window<SessionResponse> getSessionsForUser(Long userId, CursorPaginationRequest req) {
        Specification<StudySession> spec = StudySessionSpecifications.hasUser(userId);
        CursorPaginationRequest pagination = CursorPaginationRequest.forSessions(
            req.lastId(), req.cursorValue(), req.pageSize(), req.direction());

        Window<StudySession> sessionWindow = studySessionRepo.findBy(spec, query -> query
            .limit(pagination.pageSize())
            .sortBy(pagination.toSort())
            .scroll(pagination.toScrollPosition())
        );

        return sessionWindow.map(sessionMapper::toResponse);
    }

    /** PRIVADOS */

    private StudySession createNewSession(User user) {
        StudySession studySession = StudySession.builder().user(user).build();
        return studySessionRepo.save(studySession);
    }

    private boolean isSessionActive(CardReviewLog lastLog, User user) {
        Instant now = Instant.now(clock);

        Duration timeSinceLastReview = Duration.between(lastLog.getCreatedAt(), now);
        boolean withinThreshold = timeSinceLastReview.toMinutes() < user.getSessionThreshold();

        boolean sameAccountingDay = isSameAccountingDay(lastLog.getCreatedAt(), now, user.getZoneInfo(), user.getStartOfDay());

        return withinThreshold && sameAccountingDay;
    }

    private boolean isSameAccountingDay(Instant lastReviewTime, Instant now, String zoneInfo, int startOfDay) {
        ZoneId zoneId = TimeZoneUtils.parseOrFallback(zoneInfo, ZoneId.of("UTC"));
        ZonedDateTime lastReviewZoned = lastReviewTime.atZone(zoneId);
        ZonedDateTime nowZoned = now.atZone(zoneId);

        // Si estudio a las 2 AM y mi día empieza a las 6 AM, para el sistema aún es "ayer"
        LocalDate lastDate = lastReviewZoned.minusHours(startOfDay).toLocalDate();
        LocalDate nowDate = nowZoned.minusHours(startOfDay).toLocalDate();

        return lastDate.equals(nowDate);
    }
}
