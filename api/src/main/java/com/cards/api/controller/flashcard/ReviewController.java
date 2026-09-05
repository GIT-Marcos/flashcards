package com.cards.api.controller.flashcard;

import com.cards.api.dto.SecurityUser;
import com.cards.api.dto.request.ReviewRequest;
import com.cards.api.dto.response.CardResponse;
import com.cards.api.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Reviews", description = "Submit card reviews (spaced-repetition feedback)")
@RestController
@RequestMapping("/reviews")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @Operation(summary = "Submit a review", description = "Submits a quality rating (0-5) for a card review. Updates the spaced-repetition schedule.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Review submitted, card schedule updated"),
            @ApiResponse(responseCode = "400", description = "Validation error — ProblemDetail"),
            @ApiResponse(responseCode = "401", description = "Unauthorized — ProblemDetail"),
            @ApiResponse(responseCode = "404", description = "Card not found — ProblemDetail")
    })
    @PostMapping("/card/{cardId}")
    public ResponseEntity<CardResponse> submitReview(
            @Parameter(description = "ID of the card to review", required = true) @PathVariable Long cardId,
            @Parameter(hidden = true) @AuthenticationPrincipal SecurityUser securityUser,
            @RequestBody @Valid ReviewRequest request
    ) {
        CardResponse response = reviewService.review(cardId, securityUser.userId(), request);
        return ResponseEntity.ok(response);
    }
}
