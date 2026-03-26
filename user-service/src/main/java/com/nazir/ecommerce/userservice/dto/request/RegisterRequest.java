package com.nazir.ecommerce.userservice.dto.request;

import jakarta.validation.constraints.*;
import lombok.*;

/**
 * Payload for POST /api/v1/auth/register
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegisterRequest {

    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 50, message = "Username must be 3–50 characters")
    @Pattern(regexp = "^[a-zA-Z0-9_.-]+$", message = "Username can only contain letters, digits, underscore, dot, hyphen")
    private String username;

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email address")
    @Size(max = 255)
    private String email;

    /**
     * Password rules enforced client-side AND server-side.
     * The regex requires at least one uppercase, one digit, one special char.
     */
    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 128, message = "Password must be 8–128 characters")
    @Pattern(regexp = "^(?=.*[A-Z])(?=.*[0-9])(?=.*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?]).+$", message = "Password must contain at least one uppercase letter, one digit, and one special character")
    private String password;

    @NotBlank(message = "First name is required")
    @Size(max = 100, message = "First name max 100 characters")
    private String firstName;

    @Size(max = 100, message = "Last name max 100 characters")
    private String lastName;

    /**
     * E.164 international phone format: +91XXXXXXXXXX, +1XXXXXXXXXX
     */
    @Pattern(regexp = "^\\+?[1-9]\\d{6,14}$", message = "Phone number must be a valid international number (e.g. +911234567890)")
    private String phoneNumber;
}
