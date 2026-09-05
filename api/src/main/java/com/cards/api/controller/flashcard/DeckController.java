package com.cards.api.controller.flashcard;

import com.cards.api.dto.SecurityUser;
import com.cards.api.dto.request.AiTopicRequest;
import com.cards.api.dto.request.CreateDeckRequest;
import com.cards.api.dto.request.CursorPaginationRequest;
import com.cards.api.dto.request.PatchDeckRequest;
import com.cards.api.dto.response.AiGenerationResponse;
import com.cards.api.dto.response.DeckResponse;
import com.cards.api.service.DeckService;
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

@Tag(name = "Decks", description = "Flashcard deck CRUD and browsing")
@RestController
@RequestMapping("/decks")
public class DeckController {

    private final DeckService deckService;
    private final AiCardGeneratorService aiService;

    public DeckController(DeckService deckService, AiCardGeneratorService aiService) {
        this.deckService = deckService;
        this.aiService = aiService;
    }

    @Operation(summary = "Create a deck", description = "Creates a new flashcard deck for the authenticated user.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Deck created"),
        @ApiResponse(responseCode = "400", description = "Validation error — ProblemDetail"),
        @ApiResponse(responseCode = "401", description = "Unauthorized — ProblemDetail")
    })
    @PostMapping
    public ResponseEntity<DeckResponse> create(
        @Parameter(hidden = true) @AuthenticationPrincipal SecurityUser securityUser,
        @RequestBody @Valid CreateDeckRequest request) {
        DeckResponse response = deckService.create(securityUser.userId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Generate deck with AI", description = "Creates a new deck and generates flashcards from a .txt or .pdf file using AI.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Deck and cards generated"),
        @ApiResponse(responseCode = "400", description = "Validation error — ProblemDetail"),
        @ApiResponse(responseCode = "401", description = "Unauthorized — ProblemDetail"),
        @ApiResponse(responseCode = "404", description = "API key not found — ProblemDetail"),
        @ApiResponse(responseCode = "502", description = "AI generation failed — ProblemDetail")
    })
    @PostMapping("/ai")
    public ResponseEntity<AiGenerationResponse> generateDeck(
        @Parameter(hidden = true) @AuthenticationPrincipal SecurityUser securityUser,
        @Parameter(description = "File to extract text from (.txt or .pdf)", required = true) @RequestParam("file") MultipartFile file,
        @Parameter(description = "AI provider to use", required = true) @RequestParam("provider") AiProvider provider,
        @Parameter(description = "Name for the new deck", required = true) @RequestParam("deckName") String deckName,
        @Parameter(description = "Model override (required for OpenRouter, optional for others)") @RequestParam(value = "model", required = false) String model
    ) {
        try {
            var response = aiService.generateDeckWithCards(
                securityUser.userId(),
                file.getOriginalFilename(), file.getBytes(),
                provider, deckName, model
            );
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (java.io.IOException e) {
            throw new RuntimeException("Failed to read uploaded file", e);
        }
    }

    @Operation(summary = "Generate deck from topic with AI", description = "Creates a new deck and generates flashcards from a topic prompt using AI.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Deck and cards generated"),
        @ApiResponse(responseCode = "400", description = "Validation error — ProblemDetail"),
        @ApiResponse(responseCode = "401", description = "Unauthorized — ProblemDetail"),
        @ApiResponse(responseCode = "404", description = "API key not found — ProblemDetail"),
        @ApiResponse(responseCode = "502", description = "AI generation failed — ProblemDetail")
    })
    @PostMapping("/ai/topic")
    public ResponseEntity<AiGenerationResponse> generateDeckFromTopic(
        @Parameter(hidden = true) @AuthenticationPrincipal SecurityUser securityUser,
        @RequestBody @Valid AiTopicRequest request) {
        var response = aiService.generateDeckFromTopic(
            securityUser.userId(), request.prompt(), request.provider(),
            request.deckName(), request.model());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Update a deck", description = "Updates the name of an existing deck.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Deck updated"),
        @ApiResponse(responseCode = "400", description = "Validation error — ProblemDetail"),
        @ApiResponse(responseCode = "401", description = "Unauthorized — ProblemDetail"),
        @ApiResponse(responseCode = "404", description = "Deck not found — ProblemDetail")
    })
    @PatchMapping("/{deckId}")
    public ResponseEntity<DeckResponse> patch(
        @Parameter(description = "ID of the deck to update", required = true) @PathVariable Long deckId,
        @Parameter(hidden = true) @AuthenticationPrincipal SecurityUser securityUser,
        @RequestBody @Valid PatchDeckRequest request) {
        DeckResponse response = deckService.patch(deckId, securityUser.userId(), request);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Delete a deck", description = "Permanently deletes a deck and all its cards.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Deck deleted"),
        @ApiResponse(responseCode = "401", description = "Unauthorized — ProblemDetail"),
        @ApiResponse(responseCode = "404", description = "Deck not found — ProblemDetail")
    })
    @DeleteMapping("/{deckId}")
    public ResponseEntity<Void> delete(
        @Parameter(description = "ID of the deck to delete", required = true) @PathVariable Long deckId,
        @Parameter(hidden = true) @AuthenticationPrincipal SecurityUser securityUser) {
        deckService.delete(deckId, securityUser.userId());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "List decks", description = "Returns a paginated list of decks for the authenticated user.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Paginated list of decks"),
        @ApiResponse(responseCode = "401", description = "Unauthorized — ProblemDetail")
    })
    @GetMapping
    public ResponseEntity<Window<DeckResponse>> getDecks(
        @Parameter(hidden = true) @AuthenticationPrincipal SecurityUser securityUser,
        @Parameter(name = "cursorValue", description = "createdAt of the last deck from the previous page (ISO-8601)", example = "2026-05-19T10:30:00Z")
        @Valid CursorPaginationRequest req) {
        Window<DeckResponse> responses = deckService.getDecks(securityUser.userId(), req);
        return ResponseEntity.ok(responses);
    }

    @Operation(summary = "List due decks", description = "Returns a paginated list of decks that have cards due for review.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Paginated list of due decks"),
        @ApiResponse(responseCode = "401", description = "Unauthorized — ProblemDetail")
    })
    @GetMapping("/due")
    public ResponseEntity<Window<DeckResponse>> getDueDecks(
        @Parameter(hidden = true) @AuthenticationPrincipal SecurityUser securityUser,
        @Parameter(name = "cursorValue", description = "createdAt of the last deck from the previous page (ISO-8601)", example = "2026-05-19T10:30:00Z")
        @Valid CursorPaginationRequest req) {
        Window<DeckResponse> responses = deckService.getDueDecks(securityUser.userId(), req);
        return ResponseEntity.ok(responses);
    }
}
