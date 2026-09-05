package com.cards.api.controller.admin;

import com.cards.api.dto.response.ApiKeyResponse;
import com.cards.api.service.UserApiKeyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Admin API Keys", description = "Administrative API key operations (requires ROLE_ADMIN)")
@RestController
@RequestMapping("/admin/users/{userId}/api-keys")
@PreAuthorize("hasRole('ADMIN')")
public class AdminApiKeyController {

    private final UserApiKeyService service;

    public AdminApiKeyController(UserApiKeyService service) {
        this.service = service;
    }

    @Operation(summary = "List user API keys", description = "Returns all API keys for a specific user (key values are never exposed).")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "List of API keys"),
        @ApiResponse(responseCode = "401", description = "Unauthorized — ProblemDetail"),
        @ApiResponse(responseCode = "403", description = "Access denied — ProblemDetail")
    })
    @GetMapping
    public ResponseEntity<List<ApiKeyResponse>> listUserKeys(
        @Parameter(description = "ID of the user", required = true) @PathVariable Long userId) {
        return ResponseEntity.ok(service.getAdminUserKeys(userId));
    }

    @Operation(summary = "Delete user API key", description = "Permanently deletes an API key belonging to any user.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "API key deleted"),
        @ApiResponse(responseCode = "401", description = "Unauthorized — ProblemDetail"),
        @ApiResponse(responseCode = "403", description = "Access denied — ProblemDetail"),
        @ApiResponse(responseCode = "404", description = "API key not found — ProblemDetail")
    })
    @DeleteMapping("/{keyId}")
    public ResponseEntity<Void> deleteUserKey(
        @Parameter(description = "ID of the API key to delete", required = true) @PathVariable Long keyId) {
        service.adminDeleteKey(keyId);
        return ResponseEntity.noContent().build();
    }
}
