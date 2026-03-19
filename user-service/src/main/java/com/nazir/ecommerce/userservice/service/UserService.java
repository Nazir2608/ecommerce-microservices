package com.nazir.ecommerce.userservice.service;

import com.nazir.ecommerce.userservice.dto.request.ChangePasswordRequest;
import com.nazir.ecommerce.userservice.dto.request.UpdateProfileRequest;
import com.nazir.ecommerce.userservice.dto.response.UserResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/** Contract for user profile and admin operations. */
public interface UserService {

    /** Get the profile of the currently authenticated user. */
    UserResponse getMyProfile(String userId);

    /** Get any user by ID (admin or self). */
    UserResponse getUserById(UUID id);

    /** Update the profile of the authenticated user (partial update). */
    UserResponse updateMyProfile(String userId, UpdateProfileRequest request);

    /** Change password — requires current password for verification. */
    void changePassword(String userId, ChangePasswordRequest request);

    /** Soft-delete (deactivate) the authenticated user's account. */
    void deleteMyAccount(String userId);

    // ─── Admin operations ─────────────────────────────────────────────────────

    /** Paginated list of all users (ADMIN only). */
    Page<UserResponse> getAllUsers(Pageable pageable);

    /** Search users by email/username/name (ADMIN only). */
    Page<UserResponse> searchUsers(String query, Pageable pageable);

    /** Suspend a user account (ADMIN only). */
    UserResponse suspendUser(UUID userId, String reason);

    /** Activate a suspended account (ADMIN only). */
    UserResponse activateUser(UUID userId);
}
