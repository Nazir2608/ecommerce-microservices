package com.nazir.ecommerce.userservice.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nazir.ecommerce.userservice.dto.request.LoginRequest;
import com.nazir.ecommerce.userservice.dto.request.RegisterRequest;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end integration test — full application context with real containers.
 *
 * LEARNING POINT — Integration test vs Unit test:
 *   Unit test:       ~10ms, mocks dependencies, tests one class in isolation
 *   Integration test: ~10-30s, real DB + Redis + Kafka, tests the full flow
 *
 *   Strategy: write many fast unit tests, few slow integration tests.
 *   Integration tests verify that all the wiring is correct end-to-end.
 *
 * LEARNING POINT — @SpringBootTest(webEnvironment = RANDOM_PORT):
 *   Starts the FULL Spring application context on a random port.
 *   With @AutoConfigureMockMvc it exposes MockMvc for testing without
 *   making real HTTP connections (request goes through the filter chain).
 *
 * LEARNING POINT — Testcontainers with 3 containers:
 *   Each @Container is started once per test class (class-level lifecycle).
 *   @DynamicPropertySource injects each container's coordinates into Spring
 *   before the ApplicationContext initialises.
 *
 * LEARNING POINT — @TestMethodOrder + @Order:
 *   Integration tests sometimes have dependencies (login needs a registered user).
 *   @TestMethodOrder(MethodOrderer.OrderAnnotation.class) enforces execution order.
 *   This is acceptable for integration tests (not for unit tests).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Auth Integration Tests")
class AuthIntegrationTest {

    // ─── Testcontainers ───────────────────────────────────────────────────────

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("userdb_test")
            .withUsername("testuser")
            .withPassword("testpassword");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7.2-alpine")
            .withExposedPorts(6379)
            .withCommand("redis-server", "--requirepass", "testredis");

    @Container
    static KafkaContainer kafka = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.5.3"));

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // PostgreSQL
        registry.add("spring.datasource.url",      postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);

        // Redis
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379).toString());
        registry.add("spring.data.redis.password", () -> "testredis");

        // Kafka
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);

        // Disable Eureka
        registry.add("eureka.client.enabled", () -> "false");
        registry.add("spring.config.import", () -> "");
    }

    // ─── Test dependencies ────────────────────────────────────────────────────

    @Autowired private MockMvc      mvc;
    @Autowired private ObjectMapper objectMapper;

    // Shared state across ordered tests
    private static String accessToken;
    private static String refreshToken;

    // ─── Test 1: Register ─────────────────────────────────────────────────────

    @Test
    @Order(1)
    @DisplayName("1. Should register a new user and return JWT tokens")
    void register_shouldSucceed() throws Exception {
        RegisterRequest req = RegisterRequest.builder()
                .username("nazirtest")
                .email("nazirtest@example.com")
                .password("Test@12345")
                .firstName("Nazir")
                .lastName("Khan")
                .build();

        MvcResult result = mvc.perform(post("/api/v1/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.data.user.email").value("nazirtest@example.com"))
                .andReturn();

        // Save tokens for subsequent tests
        var response = objectMapper.readTree(result.getResponse().getContentAsString());
        accessToken  = response.at("/data/accessToken").asText();
        refreshToken = response.at("/data/refreshToken").asText();

        assertThat(accessToken).isNotEmpty();
        assertThat(refreshToken).isNotEmpty();
    }

    // ─── Test 2: Register duplicate ───────────────────────────────────────────

    @Test
    @Order(2)
    @DisplayName("2. Should return 409 when registering with same email")
    void register_duplicate_shouldFail() throws Exception {
        RegisterRequest req = RegisterRequest.builder()
                .username("nazirtest2")   // different username
                .email("nazirtest@example.com")  // same email!
                .password("Test@12345")
                .firstName("Other")
                .build();

        mvc.perform(post("/api/v1/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("User Already Exists"));
    }

    // ─── Test 3: Login ────────────────────────────────────────────────────────

    @Test
    @Order(3)
    @DisplayName("3. Should login and return new tokens")
    void login_shouldSucceed() throws Exception {
        LoginRequest req = new LoginRequest("nazirtest@example.com", "Test@12345");

        mvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty());
    }

    // ─── Test 4: Get profile with token ───────────────────────────────────────

    @Test
    @Order(4)
    @DisplayName("4. Should return profile when valid access token provided")
    void getProfile_withValidToken_shouldSucceed() throws Exception {
        mvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value("nazirtest@example.com"));
    }

    // ─── Test 5: Profile requires auth ────────────────────────────────────────

    @Test
    @Order(5)
    @DisplayName("5. Should return 401 when accessing profile without token")
    void getProfile_withoutToken_shouldReturn401() throws Exception {
        mvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized());
    }

    // ─── Test 6: Refresh token ────────────────────────────────────────────────

    @Test
    @Order(6)
    @DisplayName("6. Should return new tokens when refresh token is valid")
    void refresh_shouldSucceed() throws Exception {
        String body = objectMapper.writeValueAsString(
                new com.nazir.ecommerce.userservice.dto.request.RefreshTokenRequest(refreshToken));

        mvc.perform(post("/api/v1/auth/refresh")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty());
    }

    // ─── Test 7: Logout ───────────────────────────────────────────────────────

    @Test
    @Order(7)
    @DisplayName("7. Should logout and blacklist token")
    void logout_shouldSucceed() throws Exception {
        mvc.perform(post("/api/v1/auth/logout")
                        .with(csrf())
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Logged out successfully"));
    }

    // ─── Test 8: Blacklisted token rejected ───────────────────────────────────

    @Test
    @Order(8)
    @DisplayName("8. Should return 401 when using blacklisted (post-logout) access token")
    void getProfile_afterLogout_shouldReturn401() throws Exception {
        mvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isUnauthorized());
    }
}
