package com.nazir.ecommerce.userservice.service.impl;

import com.nazir.ecommerce.userservice.dto.request.ChangePasswordRequest;
import com.nazir.ecommerce.userservice.dto.request.UpdateProfileRequest;
import com.nazir.ecommerce.userservice.dto.response.UserResponse;
import com.nazir.ecommerce.userservice.event.UserEventPublisher;
import com.nazir.ecommerce.userservice.exception.PasswordMismatchException;
import com.nazir.ecommerce.userservice.exception.UserNotFoundException;
import com.nazir.ecommerce.userservice.mapper.UserMapper;
import com.nazir.ecommerce.userservice.model.User;
import com.nazir.ecommerce.userservice.repository.UserRepository;
import com.nazir.ecommerce.userservice.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserServiceImpl implements UserService {

    private final UserRepository     userRepository;
    private final PasswordEncoder    passwordEncoder;
    private final UserMapper         userMapper;
    private final UserEventPublisher eventPublisher;

    // ─── Profile ──────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public UserResponse getMyProfile(String userId) {
        User user = findById(UUID.fromString(userId));
        return userMapper.toResponse(user);
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getUserById(UUID id) {
        return userMapper.toResponse(findById(id));
    }

    @Override
    @Transactional
    public UserResponse updateMyProfile(String userId, UpdateProfileRequest request) {
        User user = findById(UUID.fromString(userId));

        /*
         * LEARNING POINT — Partial update with MapStruct:
         *   userMapper.updateEntity(request, user) applies only non-null fields
         *   from the request onto the existing user entity.
         *   NullValuePropertyMappingStrategy.IGNORE in UserMapper ensures
         *   fields not sent by the client remain unchanged.
         */
        userMapper.updateEntity(request, user);
        User saved = userRepository.save(user);

        log.info("Profile updated for userId={}", userId);
        eventPublisher.publishUserUpdated(saved);

        return userMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public void changePassword(String userId, ChangePasswordRequest request) {
        // 1. Validate confirmPassword matches newPassword
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new PasswordMismatchException("New password and confirm password do not match");
        }

        User user = findById(UUID.fromString(userId));

        // 2. Verify the current password is correct
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new PasswordMismatchException("Current password is incorrect");
        }

        // 3. Reject if new == current (no-op would be confusing to the user)
        if (passwordEncoder.matches(request.getNewPassword(), user.getPassword())) {
            throw new PasswordMismatchException("New password must be different from current password");
        }

        // 4. Hash and save
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        log.info("Password changed for userId={}", userId);
        eventPublisher.publishPasswordChanged(user);

        /*
         * LEARNING POINT — After password change:
         *   For maximum security, you should invalidate ALL existing tokens.
         *   This requires storing a "password changed at" timestamp in the JWT
         *   and checking it on every request. We log the event here and leave
         *   the full invalidation as an exercise.
         *
         *   Simple approach: call authService.logout() for all sessions.
         *   Advanced approach: include a "credential version" claim in JWT.
         */
    }

    @Override
    @Transactional
    public void deleteMyAccount(String userId) {
        User user = findById(UUID.fromString(userId));

        // Soft-delete: set status to INACTIVE, don't actually DELETE the row.
        // LEARNING POINT — Why soft delete?
        //   • Order history must survive user deletion (legal requirement)
        //   • Other services may reference this user's ID
        //   • Hard deletes break foreign keys and event sourcing audit trails
        user.setStatus(User.UserStatus.INACTIVE);
        userRepository.save(user);

        log.info("Account deactivated for userId={}", userId);
        eventPublisher.publishUserDeleted(user);
    }

    // ─── Admin ────────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public Page<UserResponse> getAllUsers(Pageable pageable) {
        return userRepository.findAll(pageable).map(userMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<UserResponse> searchUsers(String query, Pageable pageable) {
        return userRepository.search(query, pageable).map(userMapper::toResponse);
    }

    @Override
    @Transactional
    public UserResponse suspendUser(UUID userId, String reason) {
        User user = findById(userId);
        user.setStatus(User.UserStatus.SUSPENDED);
        User saved = userRepository.save(user);

        log.warn("User suspended: userId={}, reason={}", userId, reason);
        eventPublisher.publishUserSuspended(saved);

        return userMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public UserResponse activateUser(UUID userId) {
        User user = findById(userId);
        user.setStatus(User.UserStatus.ACTIVE);
        User saved = userRepository.save(user);

        log.info("User activated: userId={}", userId);
        eventPublisher.publishUserRegistered(saved); // reuse event type for simplicity

        return userMapper.toResponse(saved);
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    private User findById(UUID id) {
        return userRepository.findByIdWithRoles(id)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + id));
    }
}
