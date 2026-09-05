package com.cards.api.controller.flashcard;

import com.cards.api.dto.SecurityUser;
import com.cards.api.dto.request.CursorPaginationRequest;
import com.cards.api.dto.response.SessionResponse;
import com.cards.api.dto.response.UserStatsResponse;
import com.cards.api.service.SessionService;
import com.cards.api.service.StatsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Window;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Sessions", description = "Study session history and statistics")
@RestController
@RequestMapping("/sessions")
public class SessionController {

    private final SessionService sessionService;
    private final StatsService statsService;

    public SessionController(SessionService sessionService, StatsService statsService) {
        this.sessionService = sessionService;
        this.statsService = statsService;
    }

    @Operation(summary = "List study sessions", description = "Returns a paginated list of study sessions for the authenticated user.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Paginated list of sessions"),
        @ApiResponse(responseCode = "401", description = "Unauthorized — ProblemDetail")
    })
    @GetMapping
    public ResponseEntity<Window<SessionResponse>> getMySessions(
        @Parameter(hidden = true) @AuthenticationPrincipal SecurityUser securityUser,
        @Parameter(name = "cursorValue", description = "startTime of the last session from the previous page (ISO-8601)", example = "2026-05-19T10:30:00Z")
        @Valid CursorPaginationRequest req) {

        Window<SessionResponse> responses = sessionService.getSessionsForUser(
            securityUser.userId(),
            req
        );

        return ResponseEntity.ok(responses);
    }

    @Operation(summary = "Get user stats", description = "Returns aggregated study statistics for the authenticated user.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "User statistics retrieved"),
        @ApiResponse(responseCode = "401", description = "Unauthorized — ProblemDetail")
    })
    @GetMapping("/stats")
    public ResponseEntity<UserStatsResponse> getUserStats(
        @Parameter(hidden = true) @AuthenticationPrincipal SecurityUser securityUser) {
        UserStatsResponse response = statsService.getUserStats(securityUser.userId());
        return ResponseEntity.ok(response);
    }
}
