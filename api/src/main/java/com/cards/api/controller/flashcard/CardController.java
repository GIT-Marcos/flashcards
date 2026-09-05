package com.cards.api.controller.flashcard;

import com.cards.api.dto.SecurityUser;
import com.cards.api.dto.request.CreateCardRequest;
import com.cards.api.dto.request.CursorPaginationRequest;
import com.cards.api.dto.request.PatchCardRequest;
import com.cards.api.dto.response.AiGenerationResponse;
import com.cards.api.dto.response.CardResponse;
import com.cards.api.service.CardService;
import com.cards.api.service.ai.AiCardGeneratorService;
import com.cards.api.util.AiProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Window;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Cards", description = "Flashcard CRUD and listing within decks")
@RestController
@RequestMapping("/cards")
public class CardController {

    private final CardService service;
    private final AiCardGeneratorService aiService;

    public CardController(CardService service, AiCardGeneratorService aiService) {
        this.service = service;
        this.aiService = aiService;
    }

    @Operation(summary = "Create a card", description = "Creates a new flashcard in a specified deck.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Card created"),
        @ApiResponse(responseCode = "400", description = "Validation error — ProblemDetail"),
        @ApiResponse(responseCode = "401", description = "Unauthorized — ProblemDetail"),
        @ApiResponse(responseCode = "404", description = "Deck not found — ProblemDetail")
    })
    @PostMapping("/deck/{deckId}")
    public ResponseEntity<CardResponse> create(
        @Parameter(description = "ID of the deck to add the card to", required = true) @PathVariable Long deckId,
        @Parameter(hidden = true) @AuthenticationPrincipal SecurityUser securityUser,
        @RequestBody @Valid CreateCardRequest request
    ) {
        CardResponse response = service.create(deckId, securityUser.userId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Generate cards with AI", description = "Extracts text from a .txt or .pdf file and uses AI to generate flashcards in the specified deck.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Cards generated"),
        @ApiResponse(responseCode = "400", description = "Validation error — ProblemDetail"),
        @ApiResponse(responseCode = "401", description = "Unauthorized — ProblemDetail"),
        @ApiResponse(responseCode = "404", description = "Deck or API key not found — ProblemDetail"),
        @ApiResponse(responseCode = "502", description = "AI generation failed — ProblemDetail")
    })
    @PostMapping("/ai/deck/{deckId}")
    public ResponseEntity<AiGenerationResponse> generateCards(
        @Parameter(description = "ID of the deck to add cards to", required = true) @PathVariable Long deckId,
        @Parameter(hidden = true) @AuthenticationPrincipal SecurityUser securityUser,
        @Parameter(description = "File to extract text from (.txt or .pdf)", required = true) @RequestParam("file") MultipartFile file,
        @Parameter(description = "AI provider to use", required = true) @RequestParam("provider") AiProvider provider,
        @Parameter(description = "Model override (required for OpenRouter, optional for others)") @RequestParam(value = "model", required = false) String model
    ) {
        try {
            var response = aiService.generateCardsInDeck(
                deckId, securityUser.userId(),
                file.getOriginalFilename(), file.getBytes(),
                provider, model
            );
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (java.io.IOException e) {
            throw new RuntimeException("Failed to read uploaded file", e);
        }
    }

    @Operation(summary = "Update a card", description = "Updates the front and/or back text of a card.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Card updated"),
        @ApiResponse(responseCode = "400", description = "Validation error — ProblemDetail"),
        @ApiResponse(responseCode = "401", description = "Unauthorized — ProblemDetail"),
        @ApiResponse(responseCode = "404", description = "Card not found — ProblemDetail")
    })
    @PatchMapping("/{cardId}")
    public ResponseEntity<CardResponse> patch(
        @Parameter(description = "ID of the card to update", required = true) @PathVariable Long cardId,
        @Parameter(hidden = true) @AuthenticationPrincipal SecurityUser securityUser,
        @RequestBody @Valid PatchCardRequest request
    ) {
        CardResponse response = service.patch(cardId, securityUser.userId(), request);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Delete a card", description = "Permanently deletes a card.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Card deleted"),
        @ApiResponse(responseCode = "401", description = "Unauthorized — ProblemDetail"),
        @ApiResponse(responseCode = "404", description = "Card not found — ProblemDetail")
    })
    @DeleteMapping("/{cardId}")
    public ResponseEntity<Void> delete(
        @Parameter(description = "ID of the card to delete", required = true) @PathVariable Long cardId,
        @Parameter(hidden = true) @AuthenticationPrincipal SecurityUser securityUser) {
        service.delete(cardId, securityUser.userId());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "List pending cards by deck", description = "Returns cards due for review in a specific deck, paginated.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Paginated list of pending cards"),
        @ApiResponse(responseCode = "401", description = "Unauthorized — ProblemDetail"),
        @ApiResponse(responseCode = "404", description = "Deck not found — ProblemDetail")
    })
    @GetMapping("/deck/{deckId}/pending")
    public ResponseEntity<Window<CardResponse>> getPendingByDeck(
        @Parameter(description = "ID of the deck", required = true) @PathVariable Long deckId,
        @Parameter(hidden = true) @AuthenticationPrincipal SecurityUser securityUser,
        @Parameter(name = "cursorValue", description = "nextReviewDate of the last card from the previous page (ISO-8601)", example = "2026-05-20T15:00:00Z")
        @Valid CursorPaginationRequest req
    ) {
        Window<CardResponse> responses = service.getPendingByDeck(deckId, securityUser.userId(), req);
        return ResponseEntity.ok(responses);
    }

    @Operation(summary = "List all cards by deck", description = "Returns all cards in a specific deck, paginated.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Paginated list of cards"),
        @ApiResponse(responseCode = "401", description = "Unauthorized — ProblemDetail"),
        @ApiResponse(responseCode = "404", description = "Deck not found — ProblemDetail")
    })
    @GetMapping("/deck/{deckId}")
    public ResponseEntity<Window<CardResponse>> getByDeck(
        @Parameter(description = "ID of the deck", required = true) @PathVariable Long deckId,
        @Parameter(hidden = true) @AuthenticationPrincipal SecurityUser securityUser,
        @Parameter(name = "cursorValue", description = "nextReviewDate of the last card from the previous page (ISO-8601)", example = "2026-05-20T15:00:00Z")
        @Valid CursorPaginationRequest req
    ) {
        Window<CardResponse> window = service.getByDeck(deckId, securityUser.userId(), req);
        return ResponseEntity.ok(window);
    }

    @Operation(summary = "Get card by ID", description = "Returns a single card by its ID.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Card found"),
        @ApiResponse(responseCode = "401", description = "Unauthorized — ProblemDetail"),
        @ApiResponse(responseCode = "404", description = "Card not found — ProblemDetail")
    })
    @GetMapping("/{cardId}")
    public ResponseEntity<CardResponse> getCard(
        @Parameter(description = "ID of the card", required = true) @PathVariable Long cardId,
        @Parameter(hidden = true) @AuthenticationPrincipal SecurityUser securityUser) {
        return ResponseEntity.ok(service.getById(cardId, securityUser.userId()));
    }
}
