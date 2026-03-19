package com.nazir.ecommerce.userservice.service.impl;

import com.nazir.ecommerce.userservice.dto.request.LoginRequest;
import com.nazir.ecommerce.userservice.dto.request.RefreshTokenRequest;
import com.nazir.ecommerce.userservice.dto.request.RegisterRequest;
import com.nazir.ecommerce.userservice.dto.response.AuthResponse;
import com.nazir.ecommerce.userservice.dto.response.UserResponse;
import com.nazir.ecommerce.userservice.event.UserEventPublisher;
import com.nazir.ecommerce.userservice.exception.AccountLockedException;
import com.nazir.ecommerce.userservice.exception.InvalidTokenException;
import com.nazir.ecommerce.userservice.exception.UserAlreadyExistsException;
import com.nazir.ecommerce.userservice.mapper.UserMapper;
import com.nazir.ecommerce.userservice.model.User;
import com.nazir.ecommerce.userservice.repository.UserRepository;
import com.nazir.ecommerce.userservice.security.JwtTokenProvider;
import com.nazir.ecommerce.userservice.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Authentication service implementation.
 *
 * ┌──────────────────────────────────────────────────────────────────────────┐
 * │  Redis key schema                                                         │
 * │                                                                          │
 * │  refresh_token:{userId}   → refresh token string, TTL = 7 days          │
 * │  blacklist:{accessToken}  → "1", TTL = remaining token lifetime          │
 * └──────────────────────────────────────────────────────────────────────────┘
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {

    private static final String REFRESH_PREFIX   = "refresh_token:";
    private static final String BLACKLIST_PREFIX  = "blacklist:";

    private final UserRepository          userRepository;
    private final PasswordEncoder         passwordEncoder;
    private final JwtTokenProvider        jwtProvider;
    private final AuthenticationManager   authManager;
    private final StringRedisTemplate     redis;
    private final UserMapper              userMapper;
    private final UserEventPublisher      eventPublisher;

    // ─── Register ─────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        // 1. Uniqueness checks (fail fast before any DB write)
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new UserAlreadyExistsException(
                    "Email already registered: " + request.getEmail());
        }
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new UserAlreadyExistsException(
                    "Username already taken: " + request.getUsername());
        }

        // 2. Map DTO → entity (MapStruct-generated)
        User user = userMapper.toEntity(request);

        // 3. Hash password — NEVER store plain text
        user.setPassword(passwordEncoder.encode(request.getPassword()));

        // 4. Persist
        User saved = userRepository.save(user);
        log.info("User registered: id={}, email={}", saved.getId(), saved.getEmail());

        // 5. Publish domain event → notification-service sends welcome email
        //    Note: this is outside the transaction — if Kafka fails, user is still saved.
        //    Production: use Transactional Outbox Pattern for guaranteed delivery.
        eventPublisher.publishUserRegistered(saved);

        // 6. Issue tokens
        return buildAuthResponse(saved);
    }

    // ─── Login ────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public AuthResponse login(LoginRequest request) {
        // Load user first so we can check lock status before invoking AuthManager
        User user = userRepository.findByEmailWithRoles(request.getEmail())
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        // Check account lock (too many failed attempts)
        if (user.isAccountLocked()) {
            throw new AccountLockedException(
                    "Account is temporarily locked. Please try again later.");
        }

        try {
            /*
             * LEARNING POINT — AuthenticationManager.authenticate():
             *   Spring Security handles the actual password verification:
             *   1. Calls CustomUserDetailsService.loadUserByUsername(email)
             *   2. Compares provided password with the BCrypt hash in the DB
             *   3. Checks account disabled / locked flags
             *   4. Throws BadCredentialsException if anything fails
             *
             *   We don't call passwordEncoder.matches() directly — AuthManager does it.
             */
            authManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getEmail(),
                            request.getPassword()
                    )
            );
        } catch (BadCredentialsException ex) {
            // Track failed attempts — lock after 5
            user.recordFailedLogin();
            userRepository.save(user);
            log.warn("Failed login for email={}, attempts={}", request.getEmail(),
                    user.getFailedLoginAttempts());
            throw new BadCredentialsException("Invalid email or password");
        }

        // Successful login — clear failed attempts and update lastLoginAt
        user.recordSuccessfulLogin();
        userRepository.save(user);

        log.info("User logged in: id={}, email={}", user.getId(), user.getEmail());
        return buildAuthResponse(user);
    }

    // ─── Refresh ──────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public AuthResponse refresh(RefreshTokenRequest request) {
        String incomingToken = request.getRefreshToken();

        // 1. Validate token signature and expiry
        if (!jwtProvider.isValid(incomingToken) || !jwtProvider.isRefreshToken(incomingToken)) {
            throw new InvalidTokenException("Refresh token is invalid or expired");
        }

        // 2. Extract userId from token claims
        String userId = jwtProvider.extractUserId(incomingToken);

        // 3. Compare against stored token in Redis (rotation check)
        String redisKey = REFRESH_PREFIX + userId;
        String storedToken = redis.opsForValue().get(redisKey);

        if (storedToken == null || !storedToken.equals(incomingToken)) {
            /*
             * LEARNING POINT — Refresh token reuse detection:
             *   If the incoming token doesn't match what Redis has, someone is trying
             *   to reuse an old token (possible theft). Invalidate ALL sessions for
             *   this user as a security measure.
             */
            redis.delete(redisKey);
            log.warn("Refresh token reuse detected for userId={}. All sessions invalidated.", userId);
            throw new InvalidTokenException(
                    "Refresh token already used or revoked. Please log in again.");
        }

        // 4. Load fresh user (in case status changed since token was issued)
        User user = userRepository.findByIdWithRoles(UUID.fromString(userId))
                .orElseThrow(() -> new InvalidTokenException("User not found"));

        // 5. Delete old refresh token (rotation: each use issues a new one)
        redis.delete(redisKey);

        log.info("Token refreshed for userId={}", userId);
        return buildAuthResponse(user);
    }

    // ─── Logout ───────────────────────────────────────────────────────────────

    @Override
    public void logout(String accessToken, String userId) {
        // 1. Blacklist the access token for its remaining lifetime
        //    The JWT filter checks this key on every request
        if (accessToken != null && jwtProvider.isValid(accessToken)) {
            long remainingMs = jwtProvider.getRemainingTtlMs(accessToken);
            if (remainingMs > 0) {
                redis.opsForValue().set(
                        BLACKLIST_PREFIX + accessToken,
                        "1",
                        remainingMs,
                        TimeUnit.MILLISECONDS
                );
            }
        }

        // 2. Delete refresh token — prevents issuing new access tokens
        redis.delete(REFRESH_PREFIX + userId);

        log.info("User logged out: userId={}", userId);
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    /**
     * Generate access + refresh tokens, store refresh token in Redis, build response.
     */
    private AuthResponse buildAuthResponse(User user) {
        String accessToken  = jwtProvider.generateAccessToken(user);
        String refreshToken = jwtProvider.generateRefreshToken(user);

        // Store refresh token in Redis with TTL
        redis.opsForValue().set(
                REFRESH_PREFIX + user.getId().toString(),
                refreshToken,
                jwtProvider.getRefreshTokenExpiryMs(),
                TimeUnit.MILLISECONDS
        );

        UserResponse userResponse = userMapper.toResponse(user);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(jwtProvider.getAccessTokenExpiryMs() / 1000)  // convert ms → seconds
                .user(userResponse)
                .build();
    }
}
