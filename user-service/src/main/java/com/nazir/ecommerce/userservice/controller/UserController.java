package com.nazir.ecommerce.userservice.controller;

import com.nazir.ecommerce.userservice.dto.request.ChangePasswordRequest;
import com.nazir.ecommerce.userservice.dto.request.UpdateProfileRequest;
import com.nazir.ecommerce.userservice.dto.response.ApiResponse;
import com.nazir.ecommerce.userservice.dto.response.UserResponse;
import com.nazir.ecommerce.userservice.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * User profile and admin management endpoints.
 *
 * Base paths:
 *   /api/v1/users         — authenticated user's own operations
 *   /api/v1/admin/users   — admin-only operations
 *
 * ┌──────────────────────────────────────────────────────────────────────────┐
 * │  LEARNING POINT — @PreAuthorize                                          │
 * │                                                                          │
 * │  Method-level security. Evaluated before the method body runs.          │
 * │  Requires @EnableMethodSecurity in SecurityConfig.                       │
 * │                                                                          │
 * │  hasRole("ADMIN")     → checks GrantedAuthority "ROLE_ADMIN"            │
 * │  hasAnyRole(...)      → OR condition                                     │
 * │  #userId == principal → owner check (complex expressions)               │
 * └──────────────────────────────────────────────────────────────────────────┘
 *
 * ┌──────────────────────────────────────────────────────────────────────────┐
 * │  LEARNING POINT — Why we take userId from SecurityContext                │
 * │                                                                          │
 * │  We extract the authenticated user's ID from UserDetails (set by the    │
 * │  JWT filter), NOT from a request parameter. This prevents users from     │
 * │  passing another user's ID and accessing/modifying their data.          │
 * └──────────────────────────────────────────────────────────────────────────┘
 */
@RestController
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")    // Swagger: all endpoints require JWT
@Tag(name = "Users", description = "Profile management and admin operations")
public class UserController {

    private final UserService userService;

    // ─── Own profile ──────────────────────────────────────────────────────────

    @GetMapping("/api/v1/users/me")
    @Operation(summary = "Get my profile")
    public ResponseEntity<ApiResponse<UserResponse>> getMyProfile(
            @AuthenticationPrincipal UserDetails userDetails) {

        UserResponse response = userService.getMyProfile(userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PatchMapping("/api/v1/users/me")
    @Operation(summary = "Update my profile (partial update)")
    public ResponseEntity<ApiResponse<UserResponse>> updateMyProfile(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody UpdateProfileRequest request) {

        UserResponse response = userService.updateMyProfile(userDetails.getUsername(), request);
        return ResponseEntity.ok(ApiResponse.success(response, "Profile updated"));
    }

    @PostMapping("/api/v1/users/me/change-password")
    @Operation(summary = "Change my password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody ChangePasswordRequest request) {

        userService.changePassword(userDetails.getUsername(), request);
        return ResponseEntity.ok(ApiResponse.success(null, "Password changed successfully"));
    }

    @DeleteMapping("/api/v1/users/me")
    @Operation(summary = "Delete my account (soft delete)")
    public ResponseEntity<ApiResponse<Void>> deleteMyAccount(
            @AuthenticationPrincipal UserDetails userDetails) {

        userService.deleteMyAccount(userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.success(null, "Account deleted"));
    }

    // ─── Admin endpoints ──────────────────────────────────────────────────────

    @GetMapping("/api/v1/admin/users")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "List all users (ADMIN)", description = "Paginated, sortable")
    public ResponseEntity<ApiResponse<Page<UserResponse>>> getAllUsers(
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {

        Page<UserResponse> page = userService.getAllUsers(pageable);
        return ResponseEntity.ok(ApiResponse.success(page));
    }

    @GetMapping("/api/v1/admin/users/search")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Search users by email, username or name (ADMIN)")
    public ResponseEntity<ApiResponse<Page<UserResponse>>> searchUsers(
            @RequestParam String q,
            @PageableDefault(size = 20) Pageable pageable) {

        Page<UserResponse> page = userService.searchUsers(q, pageable);
        return ResponseEntity.ok(ApiResponse.success(page));
    }

    @GetMapping("/api/v1/admin/users/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get user by ID (ADMIN)")
    public ResponseEntity<ApiResponse<UserResponse>> getUserById(@PathVariable UUID userId) {
        return ResponseEntity.ok(ApiResponse.success(userService.getUserById(userId)));
    }

    @PutMapping("/api/v1/admin/users/{userId}/suspend")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Suspend a user account (ADMIN)")
    public ResponseEntity<ApiResponse<UserResponse>> suspendUser(
            @PathVariable UUID userId,
            @RequestParam(required = false, defaultValue = "Policy violation") String reason) {

        UserResponse response = userService.suspendUser(userId, reason);
        return ResponseEntity.ok(ApiResponse.success(response, "User suspended"));
    }

    @PutMapping("/api/v1/admin/users/{userId}/activate")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Activate a user account (ADMIN)")
    public ResponseEntity<ApiResponse<UserResponse>> activateUser(@PathVariable UUID userId) {
        UserResponse response = userService.activateUser(userId);
        return ResponseEntity.ok(ApiResponse.success(response, "User activated"));
    }
}
