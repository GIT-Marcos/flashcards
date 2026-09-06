package com.cards.api.controller;

import com.cards.api.config.ApplicationConfig;
import com.cards.api.controller.flashcard.ReviewController;
import com.cards.api.dto.SecurityUser;
import com.cards.api.dto.request.ReviewRequest;
import com.cards.api.dto.response.CardResponse;
import com.cards.api.exception.ResourceNotFoundException;
import com.cards.api.exception.domain.InvalidReviewDateException;
import com.cards.api.infraestructure.config.Config;
import com.cards.api.repo.UserRepository;
import com.cards.api.security.SecurityConfig;
import com.cards.api.service.CustomUserDetailService;
import com.cards.api.service.JwtService;
import com.cards.api.service.ReviewService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.util.List;

import static java.time.Instant.now;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

@WebMvcTest(ReviewController.class)
@Import({SecurityConfig.class, ApplicationConfig.class, Config.class})
@DisplayName("ReviewController")
class ReviewControllerTest {

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private ReviewService reviewService;
    @MockitoBean
    private JwtService jwtService;
    @MockitoBean
    private CustomUserDetailService customUserDetailService;
    @MockitoBean
    private UserRepository userRepository;

    private static final Long USER_ID = 1L;
    private static final Long CARD_ID = 100L;

    private static SecurityUser securityUser() {
        return new SecurityUser(USER_ID, "testuser", "test@email.com", "hash", "America/Buenos_Aires",
            List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    private CardResponse createCardResponse() {
        return new CardResponse(CARD_ID, "hola", "hello", now());
    }

    // ======================== SUBMIT REVIEW ========================

    @Nested
    @DisplayName("POST /reviews/card/{cardId}")
    class SubmitReview {

        @Test
        @DisplayName("should return 200 with updated card after successful review")
        void shouldSubmitReviewSuccessfully() {
            when(reviewService.review(anyLong(), anyLong(), any(ReviewRequest.class)))
                .thenReturn(createCardResponse());

            String body = """
                {
                    "quality": 4
                }
                """;

            var result = assertThat(mvc.post().uri("/reviews/card/{cardId}", CARD_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(user(securityUser())));
            result.hasStatusOk();
            result.bodyJson().extractingPath("$.id").asNumber().matches(n -> n.longValue() == CARD_ID);
            result.bodyJson().extractingPath("$.front").asString().isEqualTo("hola");
            result.bodyJson().extractingPath("$.back").asString().isEqualTo("hello");
        }

        @Test
        @DisplayName("should return 400 when quality is null")
        void shouldReturn400WhenQualityNull() {
            String body = "{}";

            assertThat(mvc.post().uri("/reviews/card/{cardId}", CARD_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(user(securityUser())))
                .hasStatus(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("should return 400 when quality is below minimum (0)")
        void shouldReturn400WhenQualityBelowMin() {
            String body = """
                {
                    "quality": -1
                }
                """;

            assertThat(mvc.post().uri("/reviews/card/{cardId}", CARD_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(user(securityUser())))
                .hasStatus(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("should return 400 when quality exceeds maximum (5)")
        void shouldReturn400WhenQualityAboveMax() {
            String body = """
                {
                    "quality": 6
                }
                """;

            assertThat(mvc.post().uri("/reviews/card/{cardId}", CARD_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(user(securityUser())))
                .hasStatus(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("should return 200 with quality at minimum boundary (0)")
        void shouldAcceptQualityAtMin() {
            when(reviewService.review(anyLong(), anyLong(), any(ReviewRequest.class)))
                .thenReturn(createCardResponse());

            String body = """
                {
                    "quality": 0
                }
                """;

            var result = assertThat(mvc.post().uri("/reviews/card/{cardId}", CARD_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(user(securityUser())));
            result.hasStatusOk();
            result.bodyJson().extractingPath("$.id").asNumber().matches(n -> n.longValue() == CARD_ID);
            result.bodyJson().extractingPath("$.front").asString().isEqualTo("hola");
            result.bodyJson().extractingPath("$.back").asString().isEqualTo("hello");
        }

        @Test
        @DisplayName("should return 200 with quality at maximum boundary (5)")
        void shouldAcceptQualityAtMax() {
            when(reviewService.review(anyLong(), anyLong(), any(ReviewRequest.class)))
                .thenReturn(createCardResponse());

            String body = """
                {
                    "quality": 5
                }
                """;

            var result = assertThat(mvc.post().uri("/reviews/card/{cardId}", CARD_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(user(securityUser())));
            result.hasStatusOk();
            result.bodyJson().extractingPath("$.id").asNumber().matches(n -> n.longValue() == CARD_ID);
            result.bodyJson().extractingPath("$.front").asString().isEqualTo("hola");
            result.bodyJson().extractingPath("$.back").asString().isEqualTo("hello");
        }

        @Test
        @DisplayName("should return 404 when card not found")
        void shouldReturn404WhenCardNotFound() {
            when(reviewService.review(anyLong(), anyLong(), any(ReviewRequest.class)))
                .thenThrow(new ResourceNotFoundException("Card not found"));

            String body = """
                {
                    "quality": 3
                }
                """;

            assertThat(mvc.post().uri("/reviews/card/{cardId}", CARD_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(user(securityUser())))
                .hasStatus(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("should return 409 when optimistic locking conflict occurs")
        void shouldReturn409OnOptimisticLock() {
            when(reviewService.review(anyLong(), anyLong(), any(ReviewRequest.class)))
                .thenThrow(new ObjectOptimisticLockingFailureException(
                    "Card", CARD_ID, null));

            String body = """
                {
                    "quality": 3
                }
                """;

            assertThat(mvc.post().uri("/reviews/card/{cardId}", CARD_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(user(securityUser())))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson()
                .extractingPath("$.title").asString().isEqualTo("Conflict");
        }

        @Test
        @DisplayName("should return 400 when card is not yet due for review")
        void shouldReturn400WhenCardNotDue() {
            when(reviewService.review(anyLong(), anyLong(), any(ReviewRequest.class)))
                .thenThrow(new InvalidReviewDateException());

            String body = """
                {
                    "quality": 3
                }
                """;

            assertThat(mvc.post().uri("/reviews/card/{cardId}", CARD_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(user(securityUser())))
                .hasStatus(HttpStatus.BAD_REQUEST);
        }
    }
}
