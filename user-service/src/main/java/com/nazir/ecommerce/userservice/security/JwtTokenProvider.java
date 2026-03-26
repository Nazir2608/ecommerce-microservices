package com.nazir.ecommerce.userservice.security;

import com.nazir.ecommerce.userservice.model.User;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * JWT utility — creates and validates access + refresh tokens.
 * <p>
 * — JWT structure
 * A JWT has three Base64URL-encoded parts: header.payload.signature
 * header:   { "alg": "HS256", "typ": "JWT" }
 * payload:  { "sub": "uuid", "email": "...", "roles": "...", "exp": ... }
 * signature: HMACSHA256(base64(header) + "." + base64(payload), secret)
 * The payload is BASE64 encoded — NOT encrypted.
 * Anyone can decode and read it. Never put passwords or secrets in it.
 * The SIGNATURE ensures the token was not tampered with.
 * A server with the same secret can verify integrity in ~1ms with no DB.
 * <p>
 * — Signing algorithm (HS256 vs RS256)
 * <p>
 * HS256 (HMAC-SHA256): symmetric — same secret signs and verifies.
 * + Simple, fast
 * - All services that verify must know the secret
 * <p>
 * RS256 (RSA-SHA256):  asymmetric — private key signs, public key verifies
 * + Only auth service has the private key
 * + Other services verify with the public key (safe to share)
 * - Slower, more complex setup
 * <p>
 * For this project we use HS256 (simpler to start).
 * In production with many services, consider RS256.
 */
@Component
@Slf4j
public class JwtTokenProvider {

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.access-token-expiry-ms:86400000}")        // default 24 h
    private long accessTokenExpiryMs;

    @Value("${jwt.refresh-token-expiry-ms:604800000}")      // default 7 days
    private long refreshTokenExpiryMs;

    // ─── Token generation ─────────────────────────────────────────────────────

    /**
     * Generate an access token.
     * Claims embedded in the token are forwarded by the API Gateway as headers
     * to downstream services, so they don't need to re-query the DB.
     */
    public String generateAccessToken(User user) {
        String roles = user.getRoles().stream().map(User.Role::name).collect(Collectors.joining(","));

        return buildToken(user.getId().toString(), Map.of(
                        "email", user.getEmail(),
                        "username", user.getUsername(),
                        "roles", roles,
                        "type", "access"),
                accessTokenExpiryMs);
    }

    /**
     * Generate a refresh token.
     * Contains minimal claims — just subject + type.
     * The actual user info is fetched from DB when the refresh token is used.
     */
    public String generateRefreshToken(User user) {
        return buildToken(user.getId().toString(), Map.of("type", "refresh"), refreshTokenExpiryMs);
    }

    // ─── Token validation ─────────────────────────────────────────────────────

    public boolean isValid(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (ExpiredJwtException e) {
            log.warn("JWT expired: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            log.warn("Unsupported JWT: {}", e.getMessage());
        } catch (MalformedJwtException e) {
            log.warn("Malformed JWT: {}", e.getMessage());
        } catch (SecurityException e) {
            log.warn("Invalid JWT signature: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("Empty JWT: {}", e.getMessage());
        }
        return false;
    }

    public boolean isRefreshToken(String token) {
        try {
            Claims claims = parseClaims(token);
            return "refresh".equals(claims.get("type", String.class));
        } catch (Exception e) {
            return false;
        }
    }

    // ─── Claims extraction ────────────────────────────────────────────────────

    public Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String extractUserId(String token) {
        return parseClaims(token).getSubject();
    }

    public String extractEmail(String token) {
        return parseClaims(token).get("email", String.class);
    }

    public long getRemainingTtlMs(String token) {
        Date expiration = parseClaims(token).getExpiration();
        return Math.max(0, expiration.getTime() - System.currentTimeMillis());
    }

    // ─── Getters ──────────────────────────────────────────────────────────────

    public long getAccessTokenExpiryMs() {
        return accessTokenExpiryMs;
    }

    public long getRefreshTokenExpiryMs() {
        return refreshTokenExpiryMs;
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    private String buildToken(String subject, Map<String, Object> extraClaims, long ttlMs) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + ttlMs);

        return Jwts.builder()
                .subject(subject)
                .claims(extraClaims)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey())
                .compact();
    }

    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }
}

