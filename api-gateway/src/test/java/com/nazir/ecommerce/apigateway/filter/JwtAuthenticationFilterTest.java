package com.nazir.ecommerce.apigateway.filter;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.assertj.core.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("JwtAuthenticationFilter")
class JwtAuthenticationFilterTest {

    private JwtAuthenticationFilter filter;
    private static final String SECRET = "NazirEcommerceSecretKeyMustBeAtLeast256BitsLongForHS256Algorithm!";

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter();
        ReflectionTestUtils.setField(filter, "jwtSecret", SECRET);
    }

    private String buildToken(String userId, String email) {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .subject(userId)
                .claim("email", email)
                .claim("roles", "ROLE_CUSTOMER")
                .expiration(new Date(System.currentTimeMillis() + 3600_000))
                .signWith(key)
                .compact();
    }

    @Test
    @DisplayName("should pass request with valid JWT and add X-Auth headers")
    void validJwt_passesThrough() {
        String token = buildToken("user-123", "nazir@example.com");

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/v1/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build();

        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        var gatewayFilter = filter.apply(new JwtAuthenticationFilter.Config());

        // chain.filter() just records the mutated request
        final ServerWebExchange[] captured = new ServerWebExchange[1];

        StepVerifier.create(
                        gatewayFilter.filter(exchange, ex -> {
                            captured[0] = ex;
                            return Mono.empty();
                        }))
                .verifyComplete();

        // Verify downstream headers were set
        assertThat(captured[0].getRequest().getHeaders().getFirst("X-Auth-User-Id"))
                .isEqualTo("user-123");
        assertThat(captured[0].getRequest().getHeaders().getFirst("X-Auth-User-Email"))
                .isEqualTo("nazir@example.com");
    }

    @Test
    @DisplayName("should return 401 when Authorization header is missing")
    void missingHeader_returns401() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/v1/users/me")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        var gatewayFilter = filter.apply(new JwtAuthenticationFilter.Config());

        StepVerifier.create(
                        gatewayFilter.filter(exchange, ex -> Mono.empty()))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("should return 401 when token is expired")
    void expiredToken_returns401() {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        String expiredToken = Jwts.builder()
                .subject("user-123")
                .expiration(new Date(System.currentTimeMillis() - 3600_000)) // 1h ago
                .signWith(key)
                .compact();

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/v1/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredToken)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        var gatewayFilter = filter.apply(new JwtAuthenticationFilter.Config());

        StepVerifier.create(
                        gatewayFilter.filter(exchange, ex -> Mono.empty()))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("should return 401 when token is tampered")
    void tamperedToken_returns401() {
        String token = buildToken("user-123", "nazir@example.com");
        String tampered = token.substring(0, token.length() - 5) + "XXXXX"; // corrupt signature

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/v1/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tampered)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        var gatewayFilter = filter.apply(new JwtAuthenticationFilter.Config());

        StepVerifier.create(
                        gatewayFilter.filter(exchange, ex -> Mono.empty()))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("should return 401 when Authorization format is not Bearer")
    void wrongFormat_returns401() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/v1/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Basic dXNlcjpwYXNz")  // Basic auth
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        var gatewayFilter = filter.apply(new JwtAuthenticationFilter.Config());

        StepVerifier.create(
                        gatewayFilter.filter(exchange, ex -> Mono.empty()))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
