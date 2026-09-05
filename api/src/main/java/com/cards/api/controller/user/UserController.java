package com.cards.api.controller.user;

import com.cards.api.dto.SecurityUser;
import com.cards.api.dto.request.PatchUserRequest;
import com.cards.api.dto.response.UserResponse;
import com.cards.api.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Users", description = "Current user profile operations")
@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @Operation(summary = "Get current user", description = "Returns the profile of the currently authenticated user.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User profile retrieved"),
            @ApiResponse(responseCode = "401", description = "Unauthorized — ProblemDetail")
    })
    @GetMapping("/me")
    public ResponseEntity<UserResponse> getCurrentUser(
            @Parameter(hidden = true) @AuthenticationPrincipal SecurityUser securityUser) {
        UserResponse response = userService.getCurrent(securityUser.userId());
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Update current user", description = "Updates the profile of the currently authenticated user. Only provided fields are updated.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User profile updated"),
            @ApiResponse(responseCode = "400", description = "Validation error — ProblemDetail"),
            @ApiResponse(responseCode = "401", description = "Unauthorized — ProblemDetail")
    })
    @PatchMapping("/me")
    public ResponseEntity<UserResponse> patch(
            @Parameter(hidden = true) @AuthenticationPrincipal SecurityUser securityUser,
            @RequestBody @Valid PatchUserRequest request) {
        UserResponse response = userService.patch(securityUser.userId(), request);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Delete current user", description = "Permanently deletes the currently authenticated user account.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Account deleted"),
            @ApiResponse(responseCode = "401", description = "Unauthorized — ProblemDetail")
    })
    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteAccount(
            @Parameter(hidden = true) @AuthenticationPrincipal SecurityUser securityUser) {
        userService.deleteUser(securityUser.userId());
        return ResponseEntity.noContent().build();
    }
}
