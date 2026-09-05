package com.cards.api.controller.user;

import com.cards.api.dto.SecurityUser;
import com.cards.api.dto.request.CreateApiKeyRequest;
import com.cards.api.dto.response.ApiKeyResponse;
import com.cards.api.service.UserApiKeyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "User API Keys", description = "Manage your own AI provider API keys")
@RestController
@RequestMapping("/users/me/api-keys")
public class UserApiKeyController {

    private final UserApiKeyService service;

    public UserApiKeyController(UserApiKeyService service) {
        this.service = service;
    }

    @Operation(summary = "List API keys", description = "Returns all API keys for the current user (key values are never exposed).")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "List of API keys"),
        @ApiResponse(responseCode = "401", description = "Unauthorized — ProblemDetail")
    })
    @GetMapping
    public ResponseEntity<List<ApiKeyResponse>> listKeys(
        @Parameter(hidden = true) @AuthenticationPrincipal SecurityUser securityUser) {
        return ResponseEntity.ok(service.getUserKeys(securityUser.userId()));
    }

    @Operation(summary = "Create API key", description = "Stores an encrypted API key for the specified AI provider.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "API key created"),
        @ApiResponse(responseCode = "400", description = "Validation error — ProblemDetail"),
        @ApiResponse(responseCode = "401", description = "Unauthorized — ProblemDetail"),
        @ApiResponse(responseCode = "409", description = "Duplicate provider — ProblemDetail")
    })
    @PostMapping
    public ResponseEntity<ApiKeyResponse> createKey(
        @Parameter(hidden = true) @AuthenticationPrincipal SecurityUser securityUser,
        @RequestBody @Valid CreateApiKeyRequest request) {
        ApiKeyResponse response = service.createKey(securityUser.userId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Delete API key", description = "Permanently deletes an API key by ID.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "API key deleted"),
        @ApiResponse(responseCode = "401", description = "Unauthorized — ProblemDetail"),
        @ApiResponse(responseCode = "404", description = "API key not found — ProblemDetail")
    })
    @DeleteMapping("/{keyId}")
    public ResponseEntity<Void> deleteKey(
        @Parameter(hidden = true) @AuthenticationPrincipal SecurityUser securityUser,
        @Parameter(description = "ID of the API key to delete", required = true) @PathVariable Long keyId) {
        service.deleteKey(securityUser.userId(), keyId);
        return ResponseEntity.noContent().build();
    }
}
