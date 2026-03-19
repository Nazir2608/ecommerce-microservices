package com.nazir.ecommerce.userservice.service;

import com.nazir.ecommerce.userservice.dto.request.LoginRequest;
import com.nazir.ecommerce.userservice.dto.request.RegisterRequest;
import com.nazir.ecommerce.userservice.dto.response.AuthResponse;
import com.nazir.ecommerce.userservice.event.UserEventPublisher;
import com.nazir.ecommerce.userservice.exception.AccountLockedException;
import com.nazir.ecommerce.userservice.exception.InvalidTokenException;
import com.nazir.ecommerce.userservice.exception.UserAlreadyExistsException;
import com.nazir.ecommerce.userservice.mapper.UserMapper;
import com.nazir.ecommerce.userservice.model.User;
import com.nazir.ecommerce.userservice.repository.UserRepository;
import com.nazir.ecommerce.userservice.security.JwtTokenProvider;
import com.nazir.ecommerce.userservice.service.impl.AuthServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

/**
 * Unit tests for AuthServiceImpl.
 *
 * LEARNING POINT — Unit test strategy:
 *   These tests isolate AuthServiceImpl from its dependencies using Mockito mocks.
 *   We test business logic only — no Spring context, no real DB, no real Redis.
 *   Each test runs in milliseconds.
 *
 * LEARNING POINT — @ExtendWith(MockitoExtension.class):
 *   Activates Mockito annotations (@Mock, @InjectMocks) without needing
 *   @SpringBootTest. Much faster than loading the full application context.
 *
 * LEARNING POINT — BDD-style (Given/When/Then):
 *   given(...).willReturn(...)  → setup mock behaviour
 *   when(...)                   → call the method under test
 *   then(...)                   → assert the result
 *   BDDMockito reads as a specification, not as implementation.
 *
 * LEARNING POINT — @Nested:
 *   Groups tests by the method they test. When the test report shows
 *   "register > should throw when email already taken", the context is clear.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthServiceImpl")
class AuthServiceImplTest {

    // ─── Mocks ────────────────────────────────────────────────────────────────
    @Mock private UserRepository       userRepository;
    @Mock private PasswordEncoder      passwordEncoder;
    @Mock private JwtTokenProvider     jwtProvider;
    @Mock private AuthenticationManager authManager;
    @Mock private StringRedisTemplate  redis;
    @Mock private ValueOperations<String, String> valueOps;  // returned by redis.opsForValue()
    @Mock private UserMapper           userMapper;
    @Mock private UserEventPublisher   eventPublisher;

    @InjectMocks
    private AuthServiceImpl authService;

    // ─── Fixtures ─────────────────────────────────────────────────────────────

    private User activeUser;
    private RegisterRequest registerRequest;

    @BeforeEach
    void setUp() {
        activeUser = User.builder()
                .id(UUID.randomUUID())
                .username("nazir")
                .email("nazir@example.com")
                .password("$2a$12$hashedPassword")
                .firstName("Nazir")
                .lastName("Khan")
                .status(User.UserStatus.ACTIVE)
                .roles(Set.of(User.Role.ROLE_CUSTOMER))
                .build();

        registerRequest = RegisterRequest.builder()
                .username("nazir")
                .email("nazir@example.com")
                .password("Test@12345")
                .firstName("Nazir")
                .lastName("Khan")
                .build();

        // Common stub: redis.opsForValue() is called on every token store
        given(redis.opsForValue()).willReturn(valueOps);
    }

    // ─── register() ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("register()")
    class RegisterTests {

        @Test
        @DisplayName("should register user and return auth response when email and username are unique")
        void register_success() {
            // Given
            given(userRepository.existsByEmail(registerRequest.getEmail())).willReturn(false);
            given(userRepository.existsByUsername(registerRequest.getUsername())).willReturn(false);
            given(userMapper.toEntity(registerRequest)).willReturn(activeUser);
            given(passwordEncoder.encode(registerRequest.getPassword())).willReturn("$2a$12$hashed");
            given(userRepository.save(any(User.class))).willReturn(activeUser);
            given(jwtProvider.generateAccessToken(activeUser)).willReturn("access-token");
            given(jwtProvider.generateRefreshToken(activeUser)).willReturn("refresh-token");
            given(jwtProvider.getRefreshTokenExpiryMs()).willReturn(604800000L);
            given(jwtProvider.getAccessTokenExpiryMs()).willReturn(86400000L);
            given(userMapper.toResponse(activeUser)).willReturn(null); // UserResponse not needed here

            // When
            AuthResponse response = authService.register(registerRequest);

            // Then
            assertThat(response.getAccessToken()).isEqualTo("access-token");
            assertThat(response.getRefreshToken()).isEqualTo("refresh-token");

            // Verify password was hashed before saving
            then(passwordEncoder).should().encode("Test@12345");
            then(userRepository).should().save(argThat(u -> u.getPassword().equals("$2a$12$hashed")));

            // Verify Kafka event was published
            then(eventPublisher).should().publishUserRegistered(activeUser);

            // Verify refresh token stored in Redis
            then(valueOps).should().set(
                    contains("refresh_token:"),
                    eq("refresh-token"),
                    eq(604800000L),
                    any()
            );
        }

        @Test
        @DisplayName("should throw UserAlreadyExistsException when email is already registered")
        void register_emailTaken_throwsException() {
            // Given
            given(userRepository.existsByEmail(registerRequest.getEmail())).willReturn(true);

            // When / Then
            assertThatThrownBy(() -> authService.register(registerRequest))
                    .isInstanceOf(UserAlreadyExistsException.class)
                    .hasMessageContaining(registerRequest.getEmail());

            // Verify no user was saved
            then(userRepository).should(never()).save(any());
            then(eventPublisher).should(never()).publishUserRegistered(any());
        }

        @Test
        @DisplayName("should throw UserAlreadyExistsException when username is already taken")
        void register_usernameTaken_throwsException() {
            // Given
            given(userRepository.existsByEmail(registerRequest.getEmail())).willReturn(false);
            given(userRepository.existsByUsername(registerRequest.getUsername())).willReturn(true);

            // When / Then
            assertThatThrownBy(() -> authService.register(registerRequest))
                    .isInstanceOf(UserAlreadyExistsException.class)
                    .hasMessageContaining(registerRequest.getUsername());
        }
    }

    // ─── login() ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("login()")
    class LoginTests {

        @Test
        @DisplayName("should return auth response on successful authentication")
        void login_success() {
            // Given
            LoginRequest req = new LoginRequest("nazir@example.com", "Test@12345");

            given(userRepository.findByEmailWithRoles(req.getEmail()))
                    .willReturn(Optional.of(activeUser));
            given(jwtProvider.generateAccessToken(activeUser)).willReturn("access-token");
            given(jwtProvider.generateRefreshToken(activeUser)).willReturn("refresh-token");
            given(jwtProvider.getRefreshTokenExpiryMs()).willReturn(604800000L);
            given(jwtProvider.getAccessTokenExpiryMs()).willReturn(86400000L);
            // authManager.authenticate() does not throw → success

            // When
            AuthResponse response = authService.login(req);

            // Then
            assertThat(response.getAccessToken()).isEqualTo("access-token");

            // Verify AuthManager was called with correct credentials
            then(authManager).should().authenticate(
                    argThat(auth ->
                        auth.getPrincipal().equals("nazir@example.com") &&
                        auth.getCredentials().equals("Test@12345")
                    )
            );

            // Verify login was recorded (resets failed attempts, updates lastLoginAt)
            then(userRepository).should().save(activeUser);
        }

        @Test
        @DisplayName("should increment failed attempts on wrong password")
        void login_wrongPassword_incrementsFailedAttempts() {
            // Given
            LoginRequest req = new LoginRequest("nazir@example.com", "WrongPass1!");

            given(userRepository.findByEmailWithRoles(req.getEmail()))
                    .willReturn(Optional.of(activeUser));
            willThrow(new BadCredentialsException("Bad credentials"))
                    .given(authManager).authenticate(any());

            // When / Then
            assertThatThrownBy(() -> authService.login(req))
                    .isInstanceOf(BadCredentialsException.class);

            // failed attempts incremented and saved
            assertThat(activeUser.getFailedLoginAttempts()).isEqualTo(1);
            then(userRepository).should().save(activeUser);
        }

        @Test
        @DisplayName("should throw AccountLockedException when account is locked")
        void login_accountLocked_throwsException() {
            // Given: user has lockedUntil set to the future
            activeUser.setLockedUntil(LocalDateTime.now().plusMinutes(10));
            LoginRequest req = new LoginRequest("nazir@example.com", "Test@12345");

            given(userRepository.findByEmailWithRoles(req.getEmail()))
                    .willReturn(Optional.of(activeUser));

            // When / Then
            assertThatThrownBy(() -> authService.login(req))
                    .isInstanceOf(AccountLockedException.class);

            // AuthManager should NOT be called when account is locked
            then(authManager).should(never()).authenticate(any());
        }
    }

    // ─── refresh() ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("refresh()")
    class RefreshTests {

        @Test
        @DisplayName("should return new tokens when refresh token is valid and matches Redis")
        void refresh_success() {
            // Given
            String refreshToken = "valid-refresh-token";
            String userId = activeUser.getId().toString();

            given(jwtProvider.isValid(refreshToken)).willReturn(true);
            given(jwtProvider.isRefreshToken(refreshToken)).willReturn(true);
            given(jwtProvider.extractUserId(refreshToken)).willReturn(userId);
            given(redis.opsForValue()).willReturn(valueOps);
            given(valueOps.get("refresh_token:" + userId)).willReturn(refreshToken);
            given(userRepository.findByIdWithRoles(activeUser.getId()))
                    .willReturn(Optional.of(activeUser));
            given(jwtProvider.generateAccessToken(activeUser)).willReturn("new-access");
            given(jwtProvider.generateRefreshToken(activeUser)).willReturn("new-refresh");
            given(jwtProvider.getRefreshTokenExpiryMs()).willReturn(604800000L);
            given(jwtProvider.getAccessTokenExpiryMs()).willReturn(86400000L);

            // When
            var req = new com.nazir.ecommerce.userservice.dto.request.RefreshTokenRequest(refreshToken);
            AuthResponse response = authService.refresh(req);

            // Then
            assertThat(response.getAccessToken()).isEqualTo("new-access");
            assertThat(response.getRefreshToken()).isEqualTo("new-refresh");

            // Old token deleted (rotation)
            then(redis).should().delete("refresh_token:" + userId);
        }

        @Test
        @DisplayName("should throw InvalidTokenException when stored token doesn't match")
        void refresh_tokenReuse_throwsException() {
            // Given: tokens don't match (reuse detected)
            String incomingToken = "old-refresh-token";
            String userId = activeUser.getId().toString();

            given(jwtProvider.isValid(incomingToken)).willReturn(true);
            given(jwtProvider.isRefreshToken(incomingToken)).willReturn(true);
            given(jwtProvider.extractUserId(incomingToken)).willReturn(userId);
            given(redis.opsForValue()).willReturn(valueOps);
            given(valueOps.get("refresh_token:" + userId)).willReturn("different-stored-token");

            // When / Then
            var req = new com.nazir.ecommerce.userservice.dto.request.RefreshTokenRequest(incomingToken);
            assertThatThrownBy(() -> authService.refresh(req))
                    .isInstanceOf(InvalidTokenException.class)
                    .hasMessageContaining("reuse");

            // All sessions invalidated on reuse
            then(redis).should().delete("refresh_token:" + userId);
        }
    }

    // ─── logout() ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("logout()")
    class LogoutTests {

        @Test
        @DisplayName("should blacklist access token and delete refresh token")
        void logout_success() {
            // Given
            String accessToken = "valid-access-token";
            String userId = activeUser.getId().toString();

            given(jwtProvider.isValid(accessToken)).willReturn(true);
            given(jwtProvider.getRemainingTtlMs(accessToken)).willReturn(3600000L); // 1h remaining

            // When
            authService.logout(accessToken, userId);

            // Then — access token blacklisted
            then(valueOps).should().set(
                    eq("blacklist:" + accessToken),
                    eq("1"),
                    eq(3600000L),
                    any()
            );
            // Refresh token deleted
            then(redis).should().delete("refresh_token:" + userId);
        }
    }
}
