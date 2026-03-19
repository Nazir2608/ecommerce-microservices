package com.nazir.ecommerce.userservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

/** Payload for POST /api/v1/auth/login */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoginRequest {

    @NotBlank(message = "Email is required")
    private String email;

    @NotBlank(message = "Password is required")
    private String password;
}
