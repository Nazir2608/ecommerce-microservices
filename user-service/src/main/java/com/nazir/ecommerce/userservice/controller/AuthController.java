package com.nazir.ecommerce.userservice.controller;

import com.nazir.ecommerce.userservice.dto.request.LoginRequest;
import com.nazir.ecommerce.userservice.dto.request.RefreshTokenRequest;
import com.nazir.ecommerce.userservice.dto.request.RegisterRequest;
import com.nazir.ecommerce.userservice.dto.response.ApiResponse;
import com.nazir.ecommerce.userservice.dto.response.AuthResponse;
import com.nazir.ecommerce.userservice.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

/**
 * Authentication endpoints — all public (no JWT required).
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Authentication", description = "Register, login, token refresh and logout")
public class AuthController {

    private final AuthService authService;


    @PostMapping("/register")
    @Operation(summary = "Register a new user", description = "Creates account and returns JWT tokens")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response, "Registration successful"));
    }

    @PostMapping("/login")
    @Operation(summary = "Authenticate user", description = "Returns access token and refresh token")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success(response, "Login successful"));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh access token", description = "Exchange a valid refresh token for a new access token. " +
            "The old refresh token is invalidated (rotation)."
    )
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        AuthResponse response = authService.refresh(request);
        return ResponseEntity.ok(ApiResponse.success(response, "Token refreshed"));
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout", description = "Blacklists the current access token and invalidates the refresh token")
    public ResponseEntity<ApiResponse<Void>> logout(HttpServletRequest request, @AuthenticationPrincipal UserDetails userDetails) {
        // Extract raw token from header so we can blacklist it
        String token = extractBearerToken(request);
        String email = userDetails.getUsername(); // Spring Security "username" = email
        authService.logout(token, email);
        return ResponseEntity.ok(ApiResponse.success(null, "Logged out successfully"));
    }

    private String extractBearerToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
}
