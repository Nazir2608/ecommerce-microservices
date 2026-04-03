package com.nazir.ecommerce.userservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nazir.ecommerce.userservice.dto.request.LoginRequest;
import com.nazir.ecommerce.userservice.dto.request.RegisterRequest;
import com.nazir.ecommerce.userservice.dto.response.AuthResponse;
import com.nazir.ecommerce.userservice.dto.response.UserResponse;
import com.nazir.ecommerce.userservice.exception.UserAlreadyExistsException;
import com.nazir.ecommerce.userservice.security.JwtTokenProvider;
import com.nazir.ecommerce.userservice.service.AuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Web layer tests for AuthController using MockMvc.
 * <p>
 * — @WebMvcTest:
 * Loads ONLY the web layer: controllers, filters, exception handlers.
 * Does NOT load: @Service, @Repository, real DB, real Kafka.
 * All @Service beans must be @MockBean — MockMvc intercepts calls.
 * Tests run in ~500ms (vs 10-30s for @SpringBootTest).
 * <p>
 * — What to test at the controller layer:
 * ✓ Request deserialization (does JSON map to DTO?)
 * ✓ Validation (does @Valid reject bad input?)
 * ✓ HTTP status codes (201 Created, 400 Bad Request, etc.)
 * ✓ Response JSON structure (correct fields, correct envelope)
 * ✓ Security (unauthenticated → 401, wrong role → 403)
 * ✗ Business logic (that's tested in service unit tests)
 * <p>
 * — @WithMockUser:
 * Injects a fake authenticated user into SecurityContextHolder.
 * Allows testing secured endpoints without going through full JWT flow.
 */
@WebMvcTest(AuthController.class)
@ActiveProfiles("test")
@DisplayName("AuthController")
class AuthControllerTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    private static final String REGISTER_URL = "/api/v1/auth/register";
    private static final String LOGIN_URL = "/api/v1/auth/login";

    private RegisterRequest validRegisterRequest() {
        return RegisterRequest.builder()
                .username("nazir")
                .email("nazir@example.com")
                .password("Test@12345")
                .firstName("Nazir")
                .lastName("Khan")
                .build();
    }

    private AuthResponse sampleAuthResponse() {
        return AuthResponse.builder()
                .accessToken("access-token-xyz")
                .refreshToken("refresh-token-xyz")
                .tokenType("Bearer")
                .expiresIn(86400L)
                .user(UserResponse.builder()
                        .id(UUID.randomUUID())
                        .email("nazir@example.com")
                        .username("nazir")
                        .build())
                .build();
    }

    @Nested
    @DisplayName("POST /api/v1/auth/register")
    class RegisterEndpointTests {

        @Test
        @DisplayName("should return 201 and auth response on successful registration")
        void register_success() throws Exception {
            given(authService.register(any(RegisterRequest.class))).willReturn(sampleAuthResponse());

            mvc.perform(post(REGISTER_URL)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRegisterRequest())))
                    .andDo(print())
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Registration successful"))
                    .andExpect(jsonPath("$.data.accessToken").value("access-token-xyz"))
                    .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                    .andExpect(jsonPath("$.data.user.email").value("nazir@example.com"));
        }

        @Test
        @DisplayName("should return 400 when email is invalid")
        void register_invalidEmail_returns400() throws Exception {
            RegisterRequest badRequest = validRegisterRequest();
            badRequest.setEmail("not-an-email");

            mvc.perform(post(REGISTER_URL)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(badRequest)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title").value("Validation Failed"))
                    .andExpect(jsonPath("$.errors.email").exists());
        }

        @Test
        @DisplayName("should return 400 when password fails strength requirements")
        void register_weakPassword_returns400() throws Exception {
            RegisterRequest badRequest = validRegisterRequest();
            badRequest.setPassword("weakpassword"); // no uppercase, no digit, no special char

            mvc.perform(post(REGISTER_URL)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(badRequest)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.password").exists());
        }

        @Test
        @DisplayName("should return 409 when email is already registered")
        void register_emailTaken_returns409() throws Exception {
            given(authService.register(any()))
                    .willThrow(new UserAlreadyExistsException("Email already registered: nazir@example.com"));

            mvc.perform(post(REGISTER_URL)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRegisterRequest())))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.title").value("User Already Exists"));
        }

        @Test
        @DisplayName("should return 400 when body is missing required fields")
        void register_missingFields_returns400() throws Exception {
            mvc.perform(post(REGISTER_URL)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.email").exists())
                    .andExpect(jsonPath("$.errors.password").exists())
                    .andExpect(jsonPath("$.errors.username").exists());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/auth/login")
    class LoginEndpointTests {

        @Test
        @DisplayName("should return 200 and tokens on valid credentials")
        void login_success() throws Exception {
            LoginRequest req = new LoginRequest("nazir@example.com", "Test@12345");
            given(authService.login(any(LoginRequest.class))).willReturn(sampleAuthResponse());

            mvc.perform(post(LOGIN_URL)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                    .andExpect(jsonPath("$.data.refreshToken").isNotEmpty());
        }

        @Test
        @DisplayName("should return 400 when login fields are blank")
        void login_blankFields_returns400() throws Exception {
            mvc.perform(post(LOGIN_URL)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"\",\"password\":\"\"}"))
                    .andExpect(status().isBadRequest());
        }
    }


    @Nested
    @DisplayName("POST /api/v1/auth/logout")
    class LogoutEndpointTests {

        @Test
        @DisplayName("should return 200 when authenticated user logs out")
        @WithMockUser(username = "nazir@example.com")
        void logout_success() throws Exception {
            mvc.perform(post("/api/v1/auth/logout")
                            .with(csrf())
                            .header("Authorization", "Bearer some-token"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("Logged out successfully"));

            then(authService).should().logout(any(), any());
        }

        @Test
        @DisplayName("should return 401 when not authenticated")
        void logout_unauthenticated_returns401() throws Exception {
            mvc.perform(post("/api/v1/auth/logout").with(csrf()))
                    .andExpect(status().isUnauthorized());
        }
    }
}
