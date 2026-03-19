package com.nazir.ecommerce.userservice.dto.response;

import lombok.*;

/**
 * Returned after successful register / login / token refresh.
 *
 * LEARNING POINT — Access token vs Refresh token:
 *
 *   accessToken   Short-lived (e.g. 15 min – 24 h).
 *                 Sent in Authorization: Bearer <token> header on every request.
 *                 Validated by API Gateway without hitting any DB.
 *
 *   refreshToken  Long-lived (e.g. 7 days).
 *                 Stored server-side in Redis (allows server-side revocation).
 *                 Used ONLY at POST /api/v1/auth/refresh to get a new accessToken.
 *                 Should be stored in HttpOnly cookie in browser clients.
 *
 *   expiresIn     Seconds until the access token expires.
 *                 Client uses this to schedule a proactive refresh before expiry.
 *
 *   tokenType     Always "Bearer" — part of OAuth 2.0 spec.
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
