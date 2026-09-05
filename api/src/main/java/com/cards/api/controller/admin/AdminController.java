package com.cards.api.controller.admin;

import com.cards.api.dto.request.CursorPaginationRequest;
import com.cards.api.dto.response.CardResponse;
import com.cards.api.dto.response.DeckResponse;
import com.cards.api.dto.response.UserResponse;
import com.cards.api.service.AdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Window;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Admin", description = "Administrative operations (requires ROLE_ADMIN)")
@RestController
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AdminService service;

    public AdminController(AdminService service) {
        this.service = service;
    }

    @Operation(summary = "List all users", description = "Returns a paginated list of all registered users.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Paginated list of users"),
        @ApiResponse(responseCode = "401", description = "Unauthorized — ProblemDetail"),
        @ApiResponse(responseCode = "403", description = "Access denied — ProblemDetail")
    })
    @GetMapping("/users")
    public ResponseEntity<Window<UserResponse>> getAllUsers(
        @Parameter(name = "cursorValue", description = "createdAt of the last user from the previous page (ISO-8601)", example = "2026-05-19T10:30:00Z")
        @Valid CursorPaginationRequest req) {
        return ResponseEntity.ok(service.getAllUsers(req));
    }

    @Operation(summary = "Get user decks", description = "Returns a paginated list of decks belonging to a specific user.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Paginated list of decks"),
        @ApiResponse(responseCode = "401", description = "Unauthorized — ProblemDetail"),
        @ApiResponse(responseCode = "403", description = "Access denied — ProblemDetail"),
        @ApiResponse(responseCode = "404", description = "User not found — ProblemDetail")
    })
    @GetMapping("/users/{userId}/decks")
    public ResponseEntity<Window<DeckResponse>> getUserDecks(
        @Parameter(description = "ID of the user", required = true) @PathVariable Long userId,
        @Parameter(name = "cursorValue", description = "createdAt of the last deck from the previous page (ISO-8601)", example = "2026-05-19T10:30:00Z")
        @Valid CursorPaginationRequest req) {
        return ResponseEntity.ok(service.getUserDecks(userId, req));
    }

    @Operation(summary = "Get deck cards", description = "Returns a paginated list of cards belonging to a specific deck.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Paginated list of cards"),
        @ApiResponse(responseCode = "401", description = "Unauthorized — ProblemDetail"),
        @ApiResponse(responseCode = "403", description = "Access denied — ProblemDetail"),
        @ApiResponse(responseCode = "404", description = "Deck not found — ProblemDetail")
    })
    @GetMapping("/decks/{deckId}/cards")
    public ResponseEntity<Window<CardResponse>> getDeckCards(
        @Parameter(description = "ID of the deck", required = true) @PathVariable Long deckId,
        @Parameter(name = "cursorValue", description = "createdAt of the last card from the previous page (ISO-8601)", example = "2026-05-19T10:30:00Z")
        @Valid CursorPaginationRequest req) {
        return ResponseEntity.ok(service.getDeckCards(deckId, req));
    }

    @Operation(summary = "Get card by ID", description = "Returns a single card by its ID.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Card found"),
        @ApiResponse(responseCode = "401", description = "Unauthorized — ProblemDetail"),
        @ApiResponse(responseCode = "403", description = "Access denied — ProblemDetail"),
        @ApiResponse(responseCode = "404", description = "Card not found — ProblemDetail")
    })
    @GetMapping("/cards/{cardId}")
    public ResponseEntity<CardResponse> getCard(
        @Parameter(description = "ID of the card", required = true) @PathVariable Long cardId) {
        CardResponse response = service.getCard(cardId);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Delete user", description = "Permanently deletes a user by ID.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "User deleted"),
        @ApiResponse(responseCode = "401", description = "Unauthorized — ProblemDetail"),
        @ApiResponse(responseCode = "403", description = "Access denied — ProblemDetail"),
        @ApiResponse(responseCode = "404", description = "User not found — ProblemDetail")
    })
    @DeleteMapping("/users/{userId}")
    public ResponseEntity<Void> deleteUser(
        @Parameter(description = "ID of the user to delete", required = true) @PathVariable Long userId) {
        service.deleteUser(userId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Delete deck", description = "Permanently deletes a deck by ID.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Deck deleted"),
        @ApiResponse(responseCode = "401", description = "Unauthorized — ProblemDetail"),
        @ApiResponse(responseCode = "403", description = "Access denied — ProblemDetail"),
        @ApiResponse(responseCode = "404", description = "Deck not found — ProblemDetail")
    })
    @DeleteMapping("/decks/{deckId}")
    public ResponseEntity<Void> deleteDeck(
        @Parameter(description = "ID of the deck to delete", required = true) @PathVariable Long deckId) {
        service.deleteDeck(deckId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Delete card", description = "Permanently deletes a card by ID.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Card deleted"),
        @ApiResponse(responseCode = "401", description = "Unauthorized — ProblemDetail"),
        @ApiResponse(responseCode = "403", description = "Access denied — ProblemDetail"),
        @ApiResponse(responseCode = "404", description = "Card not found — ProblemDetail")
    })
    @DeleteMapping("/cards/{cardId}")
    public ResponseEntity<Void> deleteCard(
        @Parameter(description = "ID of the card to delete", required = true) @PathVariable Long cardId) {
        service.deleteCard(cardId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Send review reminder notification",
               description = "Manually triggers a review reminder email to a specific user.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Notification sent successfully"),
        @ApiResponse(responseCode = "401", description = "Unauthorized — ProblemDetail"),
        @ApiResponse(responseCode = "403", description = "Access denied — ProblemDetail"),
        @ApiResponse(responseCode = "404", description = "User not found — ProblemDetail"),
        @ApiResponse(responseCode = "500", description = "Email delivery failed — ProblemDetail"),
        @ApiResponse(responseCode = "503", description = "Email delivery timed out — ProblemDetail")
    })
    @PostMapping("users/notifications/{userId}")
    public ResponseEntity<Void> sendNotification(
        @Parameter(description = "ID of the user to notify", required = true) @PathVariable Long userId) {
        service.sendNotification(userId);
        return ResponseEntity.noContent().build();
    }
}
