package com.cards.api.integration.data;

import com.cards.api.config.AuditConfig;
import com.cards.api.dto.request.CursorPaginationRequest;
import com.cards.api.entity.StudySession;
import com.cards.api.entity.User;
import com.cards.api.infraestructure.config.JpaTestConfig;
import com.cards.api.infraestructure.mother.StudySessionMother;
import com.cards.api.infraestructure.mother.UserMother;
import com.cards.api.integration.TestcontainersConfig;
import com.cards.api.repo.StudySessionRepository;
import com.cards.api.repo.UserRepository;
import com.cards.api.specification.StudySessionSpecifications;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.ScrollPosition;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Window;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

@DataJpaTest
@Import({TestcontainersConfig.class, AuditConfig.class, JpaTestConfig.class})
class StudySessionDataIntegrationTest {

    @Autowired
    private StudySessionRepository studySessionRepository;

    @Autowired
    private UserRepository userRepository;

    private User testUser;
    private User anotherUser;

    @BeforeEach
    void setUp() {
        testUser = userRepository.save(
            UserMother.createMinimal(
                UserMother.uniqueUsername("sessiontest", System.currentTimeMillis()),
                UserMother.uniqueEmail("sessiontest", System.currentTimeMillis())
            )
        );

        anotherUser = userRepository.save(
            UserMother.createMinimal(
                UserMother.uniqueUsername("otheruser", System.currentTimeMillis()),
                UserMother.uniqueEmail("otheruser", System.currentTimeMillis())
            )
        );
    }

    @Nested
    @DisplayName("StudySession Pagination: Keyset Scrolling")
    class StudySessionPaginationAndScrolling {

        @Test
        @DisplayName("should paginate WHEN specified by user")
        void shouldPaginateWhenSpecifiedByUser() {
            // Given: 4 sessions for testUser, 2 for anotherUser
            Instant baseTime = Instant.parse("2026-05-06T10:00:00Z");
            for (int i = 1; i <= 4; i++) {
                StudySession session = StudySessionMother.createForUser(testUser);
                session.setStartTime(baseTime.plusSeconds(i * 10));
                session.setCardsReviewed(i);
                studySessionRepository.save(session);
            }
            for (int i = 1; i <= 2; i++) {
                StudySession session = StudySessionMother.createForUser(anotherUser);
                session.setStartTime(baseTime.plusSeconds(i * 10));
                studySessionRepository.save(session);
            }
            studySessionRepository.flush();

            // When: Request first page (size 2) for testUser only
            CursorPaginationRequest pdr = CursorPaginationRequest.forSessions(null, null, 2, Sort.Direction.ASC);
            Specification<StudySession> spec = StudySessionSpecifications.hasUser(testUser.getId());

            Window<StudySession> firstPage = studySessionRepository.findBy(spec, q -> q
                .limit(pdr.pageSize())
                .sortBy(pdr.toSort())
                .scroll(pdr.toScrollPosition()));

            // Then: Should return exactly 2 sessions belonging to testUser
            assertSoftly(softly -> {
                softly.assertThat(firstPage.getContent())
                    .hasSize(2)
                    .extracting(StudySession::getUser)
                    .containsOnly(testUser);
                softly.assertThat(firstPage.getContent())
                    .extracting(StudySession::getCardsReviewed)
                    .containsExactly(1, 2); // ASC order by startTime
            });
        }

        @Test
        @DisplayName("should paginate WHEN multiple sessions share the same timestamp")
        void shouldPaginateWhenMultipleSessionsWithSameTimestamp() {
            // Given: 3 sessions with EXACT same startTime to force tie-break by ID
            Instant sharedInstant = Instant.parse("2026-05-06T12:00:00Z");
            StudySession session1 = StudySessionMother.createForUser(testUser);
            session1.setStartTime(sharedInstant);
            session1.setCardsReviewed(10);

            StudySession session2 = StudySessionMother.createForUser(testUser);
            session2.setStartTime(sharedInstant);
            session2.setCardsReviewed(20);

            StudySession session3 = StudySessionMother.createForUser(testUser);
            session3.setStartTime(sharedInstant);
            session3.setCardsReviewed(30);

            studySessionRepository.saveAll(java.util.List.of(session1, session2, session3));
            studySessionRepository.flush();

            // When: First page (size 2), then scroll from last position
            CursorPaginationRequest pdr = CursorPaginationRequest.forSessions(null, null, 2, Sort.Direction.ASC);
            Specification<StudySession> spec = StudySessionSpecifications.hasUser(testUser.getId());

            Window<StudySession> firstWindow = studySessionRepository.findBy(spec, q -> q
                .limit(pdr.pageSize())
                .sortBy(pdr.toSort())
                .scroll(pdr.toScrollPosition()));

            ScrollPosition lastPos = firstWindow.positionAt(firstWindow.getContent().size() - 1);
            Window<StudySession> secondWindow = studySessionRepository.findBy(spec, q -> q
                .limit(pdr.pageSize())
                .sortBy(pdr.toSort())
                .scroll(lastPos));

            // Then: Verify deterministic ordering via ID tie-breaker
            assertSoftly(softly -> {
                softly.assertThat(firstWindow.getContent()).hasSize(2);
                softly.assertThat(secondWindow.getContent()).hasSize(1);

                // All sessions belong to testUser
                softly.assertThat(firstWindow.getContent())
                    .extracting(StudySession::getUser)
                    .containsOnly(testUser);
                softly.assertThat(secondWindow.getContent())
                    .extracting(StudySession::getUser)
                    .containsOnly(testUser);

                // Verify the remaining session is the one with highest ID (session3)
                softly.assertThat(secondWindow.getContent().getFirst().getCardsReviewed())
                    .isEqualTo(30);
            });
        }

        @Test
        @DisplayName("should paginate WHEN using reverse order (DESC)")
        void shouldPaginateWhenUsingReverseOrder() {
            // Given: Sessions with distinct timestamps for deterministic DESC ordering
            Instant now = Instant.now();
            StudySession oldSession = StudySessionMother.createForUser(testUser);
            oldSession.setStartTime(now.minusSeconds(100));
            oldSession.setCardsReviewed(1);

            StudySession recentSession = StudySessionMother.createForUser(testUser);
            recentSession.setStartTime(now.minusSeconds(50));
            recentSession.setCardsReviewed(2);

            StudySession newestSession = StudySessionMother.createForUser(testUser);
            newestSession.setStartTime(now);
            newestSession.setCardsReviewed(3);

            studySessionRepository.saveAll(java.util.List.of(oldSession, recentSession, newestSession));
            studySessionRepository.flush();

            // When: Request with DESC order (newest first)
            CursorPaginationRequest pdr = CursorPaginationRequest.forSessions(null, null, 10, Sort.Direction.DESC);
            Specification<StudySession> spec = StudySessionSpecifications.hasUser(testUser.getId());

            Window<StudySession> window = studySessionRepository.findBy(spec, q -> q
                .limit(pdr.pageSize())
                .sortBy(pdr.toSort())
                .scroll(pdr.toScrollPosition()));

            // Then: Sessions should appear in reverse chronological order
            assertThat(window.getContent())
                .hasSize(3)
                .extracting(StudySession::getCardsReviewed)
                .containsExactly(3, 2, 1); // DESC: newest (3) → recent (2) → old (1)
        }
    }
}
