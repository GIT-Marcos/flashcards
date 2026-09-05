package com.cards.api.unit;

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
import com.cards.api.service.SessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.ScrollPosition;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Window;
import org.springframework.data.jpa.domain.Specification;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SessionService")
class SessionServiceTest {

    @Mock
    private StudySessionRepository studySessionRepo;
    @Mock
    private CardReviewLogRepository logRepo;
    @Mock
    private UserRepository userRepo;
    @Mock
    private SessionMapper sessionMapper;

    private Clock clock;

    private SessionService sessionService;

    private static final Long USER_ID = 1L;
    private static final Long SESSION_ID = 50L;
    private static final int USER_THRESHOLD = 30;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(
            Instant.parse("2026-05-12T10:00:00Z"),
            ZoneId.of("America/Buenos_Aires")
        );

        sessionService = new SessionService(studySessionRepo, logRepo, userRepo, sessionMapper, clock);
    }

    // ======================== HELPERS ========================

    private User createUser(int threshold, String zoneInfo) {
        User user = User.builder()
            .username("reviewer")
            .email("rev@email.com")
            .passwordHash("hash")
            .zoneInfo(zoneInfo)
            .addRole(User.UserRole.ROLE_USER)
            .build();
        user.setId(USER_ID);
        user.setSessionThreshold(threshold);
        user.setStartOfDay(6);
        return user;
    }

    private User defaultUser() {
        return createUser(USER_THRESHOLD, "America/Buenos_Aires");
    }

    private CardReviewLog createLog(Instant createdAt) {
        StudySession session = StudySession.builder()
            .user(defaultUser())
            .build();
        session.setId(SESSION_ID);
        CardReviewLog log = CardReviewLog.builder()
            .quality(3)
            .user(defaultUser())
            .studySession(session)
            .build();
        log.setCreatedAt(createdAt);
        return log;
    }

    // ======================== GET OR CREATE ACTIVE SESSION ========================

    @Nested
    @DisplayName("getOrCreateActiveSession")
    class GetOrCreateActiveSession {

        @Test
        @DisplayName("should create new session when user has no previous logs")
        void shouldCreateWhenNoPreviousLogs() {
            User user = defaultUser();

            when(userRepo.findById(USER_ID)).thenReturn(Optional.of(user));
            when(logRepo.findFirstByUser_IdOrderByCreatedAtDesc(USER_ID)).thenReturn(Optional.empty());
            when(studySessionRepo.save(any(StudySession.class))).thenAnswer(inv -> {
                StudySession s = inv.getArgument(0);
                s.setId(SESSION_ID);
                return s;
            });

            StudySession result = sessionService.getOrCreateActiveSession(USER_ID);

            assertThat(result.getId()).isEqualTo(SESSION_ID);
            assertThat(result.getUser().getId()).isEqualTo(USER_ID);
            verify(studySessionRepo).save(any(StudySession.class));
        }

        @Test
        @DisplayName("should return existing session when last review is within threshold and same day")
        void shouldReturnExistingWhenActive() {
            User user = defaultUser();
            Instant now = Instant.now(clock);
            CardReviewLog lastLog = createLog(now.minus(Duration.ofMinutes(10))); // 10 min ago, within 30 min threshold

            when(userRepo.findById(USER_ID)).thenReturn(Optional.of(user));
            when(logRepo.findFirstByUser_IdOrderByCreatedAtDesc(USER_ID)).thenReturn(Optional.of(lastLog));

            StudySession result = sessionService.getOrCreateActiveSession(USER_ID);

            assertThat(result.getId()).isEqualTo(SESSION_ID);

            verify(studySessionRepo, never()).save(any());
            verify(studySessionRepo, never()).save(any());
        }

        @Test
        @DisplayName("should create new session when threshold is exceeded")
        void shouldCreateWhenThresholdExceeded() {
            User user = defaultUser();
            Instant now = Instant.now(clock);
            CardReviewLog lastLog = createLog(now.minus(Duration.ofMinutes(60))); // Debe exceder USER_THRESHOLD

            when(userRepo.findById(USER_ID)).thenReturn(Optional.of(user));
            when(logRepo.findFirstByUser_IdOrderByCreatedAtDesc(USER_ID)).thenReturn(Optional.of(lastLog));
            when(studySessionRepo.save(any(StudySession.class))).thenAnswer(inv -> {
                StudySession s = inv.getArgument(0);
                s.setId(SESSION_ID + 1);
                return s;
            });

            StudySession result = sessionService.getOrCreateActiveSession(USER_ID);

            assertThat(result.getId()).isEqualTo(SESSION_ID + 1);
            verify(studySessionRepo).save(any(StudySession.class));
        }

        @Test
        @DisplayName("should create new session when review is from different accounting day")
        void shouldCreateOnDifferentAccountingDay() {
            clock = Clock.fixed(
                Instant.parse("2026-05-12T02:00:00Z"),
                ZoneId.of("America/Buenos_Aires")
            );

            sessionService = new SessionService(studySessionRepo, logRepo, userRepo, sessionMapper, clock);

            User user = createUser(120, "America/Buenos_Aires");

            Instant lastReview = Instant.parse("2026-05-11T23:30:00Z");

            CardReviewLog lastLog = createLog(lastReview);

            when(userRepo.findById(USER_ID)).thenReturn(Optional.of(user));
            when(logRepo.findFirstByUser_IdOrderByCreatedAtDesc(USER_ID))
                .thenReturn(Optional.of(lastLog));

            when(studySessionRepo.save(any())).thenAnswer(inv -> {
                StudySession s = inv.getArgument(0);
                s.setId(SESSION_ID + 2);
                return s;
            });

            StudySession result = sessionService.getOrCreateActiveSession(USER_ID);

            assertThat(result.getId()).isEqualTo(SESSION_ID + 2);
            verify(studySessionRepo).save(any());
        }

        @Test
        @DisplayName("should return existing session when zone is invalid (fallback to UTC)")
        void shouldNotThrowWhenZoneInvalid() {
            User user = createUser(USER_THRESHOLD, "Invalid/Zone");
            Instant now = Instant.now(clock);
            CardReviewLog lastLog = createLog(now.minus(Duration.ofMinutes(10)));

            when(userRepo.findById(USER_ID)).thenReturn(Optional.of(user));
            when(logRepo.findFirstByUser_IdOrderByCreatedAtDesc(USER_ID)).thenReturn(Optional.of(lastLog));

            StudySession result = sessionService.getOrCreateActiveSession(USER_ID);

            assertThat(result.getId()).isEqualTo(SESSION_ID);
            verify(studySessionRepo, never()).save(any());
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when user not found")
        void shouldThrowWhenUserNotFound() {
            when(userRepo.findById(USER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> sessionService.getOrCreateActiveSession(USER_ID))
                .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ======================== GET SESSIONS ========================

    @Nested
    @DisplayName("getSessionsForUser")
    class GetSessionsForUser {

        @Test
        @DisplayName("should return paginated sessions for user")
        void shouldReturnPaginatedSessions() {
            CursorPaginationRequest request = CursorPaginationRequest.forSessions(null, null, 15, Sort.Direction.DESC);
            User user = defaultUser();
            StudySession session = StudySession.builder()
                .user(user)
                .build();
            session.setId(SESSION_ID);
            session.setStartTime(Instant.now(clock).minusSeconds(600));
            session.setEndTime(Instant.now(clock));
            Window<StudySession> sessionWindow = Window.from(
                List.of(session),
                i -> ScrollPosition.keyset(),
                true
            );

            when(studySessionRepo.findBy(
                ArgumentMatchers.<Specification<StudySession>>any(),
                any()
            )).thenReturn(sessionWindow);
            when(sessionMapper.toResponse(any(StudySession.class))).thenAnswer(inv -> {
                StudySession s = inv.getArgument(0);
                return new SessionResponse(s.getId(), s.getStartTime(), s.getEndTime(),
                    s.getCardsReviewed(), s.getAccuracyRate(),
                    Duration.between(s.getStartTime(), s.getEndTime()).toSeconds());
            });

            Window<SessionResponse> result = sessionService.getSessionsForUser(USER_ID, request);

            assertThat(result.getContent()).hasSize(1);
            SessionResponse response = result.getContent().getFirst();
            assertThat(response.id()).isEqualTo(SESSION_ID);
            assertThat(response.durationSeconds()).isGreaterThan(0);
        }
    }
}
