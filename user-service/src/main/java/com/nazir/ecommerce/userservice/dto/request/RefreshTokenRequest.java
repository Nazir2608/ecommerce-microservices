package com.nazir.ecommerce.userservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

/** Payload for POST /api/v1/auth/refresh */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefreshTokenRequest {

    @NotBlank(message = "Refresh token is required")
    private String refreshToken;
}
