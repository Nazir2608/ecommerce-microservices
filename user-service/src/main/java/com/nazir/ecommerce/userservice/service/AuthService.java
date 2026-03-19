package com.nazir.ecommerce.userservice.service;

import com.nazir.ecommerce.userservice.dto.request.LoginRequest;
import com.nazir.ecommerce.userservice.dto.request.RefreshTokenRequest;
import com.nazir.ecommerce.userservice.dto.request.RegisterRequest;
import com.nazir.ecommerce.userservice.dto.response.AuthResponse;

/**
 * Contract for authentication operations.
 *
 * LEARNING POINT — Interface + Implementation pattern:
 *   Separating the interface from the implementation allows:
 *   • Swap implementations without changing callers (e.g. OAuth2 auth)
 *   • Mock the interface easily in unit tests
 *   • Clear contract documentation at the interface level
 *   • Spring creates a proxy (for @Transactional) on the interface
 */
public interface AuthService {

    /**
     * Register a new user and return tokens.
     *
     * @throws com.nazir.ecommerce.userservice.exception.UserAlreadyExistsException
     *         if email or username is already taken
     */
    AuthResponse register(RegisterRequest request);

    /**
     * Authenticate user credentials and return tokens.
     *
     * @throws org.springframework.security.authentication.BadCredentialsException
     *         if credentials are invalid
     * @throws com.nazir.ecommerce.userservice.exception.AccountLockedException
     *         if account is temporarily locked
     */
    AuthResponse login(LoginRequest request);

    /**
     * Exchange a valid refresh token for a new access token (+ rotated refresh token).
     *
     * @throws com.nazir.ecommerce.userservice.exception.InvalidTokenException
     *         if the refresh token is invalid, expired, or was already used
     */
    AuthResponse refresh(RefreshTokenRequest request);

    /**
     * Invalidate both the access token and the refresh token for the given user.
     *
     * @param accessToken  the raw Bearer token from the Authorization header
     * @param userId       the authenticated user's ID (from Security context)
     */
    void logout(String accessToken, String userId);
}
