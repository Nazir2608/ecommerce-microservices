package com.nazir.ecommerce.userservice.dto.response;

import lombok.*;

/**
 * Returned after successful register / login / token refresh.
 * <p>
 * Access token vs Refresh token:
 * <p>
 * accessToken   Short-lived (e.g. 15 min – 24 h).
 * Sent in Authorization: Bearer <token> header on every request.
 * Validated by API Gateway without hitting any DB.
 * <p>
 * refreshToken  Long-lived (e.g. 7 days).
 * Stored server-side in Redis (allows server-side revocation).
 * Used ONLY at POST /api/v1/auth/refresh to get a new accessToken.
 * Should be stored in HttpOnly cookie in browser clients.
 * <p>
 * tokenType     Always "Bearer" — part of OAuth 2.0 spec.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthResponse {

    private String accessToken;
    private String refreshToken;

    @Builder.Default
    private String tokenType = "Bearer";

    private long expiresIn;       // seconds

    private UserResponse user;
}
