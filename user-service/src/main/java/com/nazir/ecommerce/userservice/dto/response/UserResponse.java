package com.nazir.ecommerce.userservice.dto.response;

import com.nazir.ecommerce.userservice.model.User;
import lombok.*;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

/**
 * Response DTO for a single user — returned by profile and admin endpoints.
 *
 * LEARNING POINT — What to exclude from response DTOs:
 *   • password   → NEVER
 *   • internal audit fields like failedLoginAttempts → omit from public API
 *   • lockedUntil → omit (don't tell attackers the lock duration)
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserResponse {

    private UUID id;
    private String username;
    private String email;
    private String firstName;
    private String lastName;
    private String phoneNumber;
    private String profileImageUrl;
    private User.UserStatus status;
    private Set<User.Role> roles;
    private boolean emailVerified;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime lastLoginAt;
}
