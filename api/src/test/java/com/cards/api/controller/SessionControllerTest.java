package com.cards.api.controller;

import com.cards.api.config.ApplicationConfig;
import com.cards.api.controller.flashcard.SessionController;
import com.cards.api.dto.SecurityUser;
import com.cards.api.dto.response.SessionResponse;
import com.cards.api.dto.response.UserStatsResponse;
import com.cards.api.infraestructure.config.Config;
import com.cards.api.repo.UserRepository;
import com.cards.api.security.SecurityConfig;
import com.cards.api.service.CustomUserDetailService;
import com.cards.api.service.JwtService;
import com.cards.api.service.SessionService;
import com.cards.api.service.StatsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.ScrollPosition;
import org.springframework.data.domain.Window;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static java.time.Instant.now;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

@WebMvcTest(SessionController.class)
@Import({SecurityConfig.class, ApplicationConfig.class, Config.class})
@DisplayName("SessionController")
class SessionControllerTest {

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private SessionService sessionService;
    @MockitoBean
    private StatsService statsService;
    @MockitoBean
    private JwtService jwtService;
    @MockitoBean
    private CustomUserDetailService customUserDetailService;
    @MockitoBean
    private UserRepository userRepository;

    private static final Long USER_ID = 1L;
    private static final Long SESSION_ID = 50L;

    private static SecurityUser securityUser() {
        return new SecurityUser(USER_ID, "testuser", "test@email.com", "hash", "America/Buenos_Aires",
            List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    private Window<SessionResponse> createSessionWindow() {
        var start = now().minusSeconds(600);
        var end = now();
        SessionResponse response = new SessionResponse(SESSION_ID, start, end, 10, 0.8,
            Duration.between(start, end).toSeconds());
        return Window.from(
            List.of(response),
            i -> ScrollPosition.keyset(),
            false
        );
    }

    // ======================== GET SESSIONS ========================

    @Nested
    @DisplayName("GET /sessions")
    class GetMySessions {

        @Test
        @DisplayName("should return 200 with paginated sessions")
        void shouldReturnPaginatedSessions() {
            when(sessionService.getSessionsForUser(anyLong(), any())).thenReturn(createSessionWindow());

            var result = assertThat(mvc.get().uri("/sessions")
                .with(user(securityUser())));
            result.hasStatusOk();
            result.bodyJson().extractingPath("$.content").asArray();
            result.bodyJson().extractingPath("$.content[0].id").asNumber().matches(n -> n.longValue() == SESSION_ID);
            result.bodyJson().extractingPath("$.content[0].cardsReviewed").asNumber().isEqualTo(10);
            result.bodyJson().extractingPath("$.content[0].accuracyRate").asNumber().isEqualTo(0.8);
            result.bodyJson().extractingPath("$.content[0].durationSeconds").asNumber();
        }

        @Test
        @DisplayName("should return 200 with empty content when no sessions")
        void shouldReturnEmptyWhenNoSessions() {

            Window<SessionResponse> empty = Window.from(
                List.of(),
                i -> ScrollPosition.keyset(),
                false
            );

            when(sessionService.getSessionsForUser(anyLong(), any()))
                .thenReturn(empty);

            var result = assertThat(mvc.get().uri("/sessions")
                .with(user(securityUser())));
            result.hasStatusOk();
            result.bodyJson().extractingPath("$.content").asArray().isEmpty();
        }

    }

    // ======================== GET STATS ========================

    @Nested
    @DisplayName("GET /sessions/stats")
    class GetStats {

        private UserStatsResponse createStatsResponse() {
            return new UserStatsResponse(100L, 0.85, 20L, 500L, Map.of(1, 10L, 3, 30L, 5, 60L));
        }

        @Test
        @DisplayName("should return 200 with user stats")
        void shouldReturnStats() {
            when(statsService.getUserStats(anyLong())).thenReturn(createStatsResponse());

            var result = assertThat(mvc.get().uri("/sessions/stats")
                .with(user(securityUser())));
            result.hasStatusOk();
            result.bodyJson().extractingPath("$.totalReviews").asNumber().matches(n -> n.longValue() == 100L);
            result.bodyJson().extractingPath("$.totalSessions").asNumber().matches(n -> n.longValue() == 20L);
            result.bodyJson().extractingPath("$.totalCardsReviewed").asNumber().matches(n -> n.longValue() == 500L);
            result.bodyJson().extractingPath("$.qualityDistribution['1']").asNumber().matches(n -> n.longValue() == 10L);
            result.bodyJson().extractingPath("$.qualityDistribution['3']").asNumber().matches(n -> n.longValue() == 30L);
            result.bodyJson().extractingPath("$.qualityDistribution['5']").asNumber().matches(n -> n.longValue() == 60L);
        }

        @Test
        @DisplayName("should return 403 without authentication")
        void shouldReturn403() {
            assertThat(mvc.get().uri("/sessions/stats"))
                .hasStatus(HttpStatus.FORBIDDEN);
        }
    }
}
