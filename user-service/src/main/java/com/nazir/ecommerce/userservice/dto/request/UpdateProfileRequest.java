package com.nazir.ecommerce.userservice.dto.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * Payload for PATCH /api/v1/users/me
 *
 * All fields are optional — client sends only what it wants to change.
 * The service layer applies only non-null fields (partial update).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateProfileRequest {

    @Size(max = 100)
    private String firstName;

    @Size(max = 100)
    private String lastName;

    @Pattern(regexp = "^\\+?[1-9]\\d{6,14}$", message = "Invalid phone number")
    private String phoneNumber;

    @Size(max = 500)
    private String profileImageUrl;
}
