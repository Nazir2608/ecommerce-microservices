package com.nazir.ecommerce.userservice.security;

import com.nazir.ecommerce.userservice.model.User;
import com.nazir.ecommerce.userservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Bridge between our User entity and Spring Security's UserDetails interface.
 * <p>
 * Spring Security authentication flow:
 * <p>
 * 1. Client sends POST /api/v1/auth/login with email + password
 * 2. AuthService calls AuthenticationManager.authenticate(...)
 * 3. AuthenticationManager calls UserDetailsService.loadUserByUsername(email)
 * 4. THIS class fetches the User from DB and wraps it in UserDetails
 * 5. Spring Security compares stored hashed password with provided password (BCrypt)
 * 6. If match → authentication success → AuthService generates JWT
 * <p>
 * — @Transactional here:
 * The User entity has roles as @ElementCollection (separate table).
 * Without @Transactional the session closes after findByEmailWithRoles() returns,
 * and accessing user.getRoles() would throw LazyInitializationException.
 *
 * @Transactional keeps the session open until the method returns.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmailWithRoles(email).orElseThrow(() -> {
            log.warn("User not found for email: {}", email);
            return new UsernameNotFoundException("User not found: " + email);
        });

        // Convert our Role enum to Spring Security's GrantedAuthority
        Set<SimpleGrantedAuthority> authorities = user.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority(role.name()))
                .collect(Collectors.toSet());

        /**
         * org.springframework.security.core.userdetails.User.builder() creates
         * a standard UserDetails implementation.
         * accountExpired    → set to false (we use our own UserStatus for this)
         * credentialsExpired → false
         * enabled           → false only if SUSPENDED or INACTIVE
         * locked            → mapped to our isAccountLocked() domain method
         */
        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getEmail())          // Spring Security uses this as the "username"
                .password(user.getPassword())       // BCrypt hash — Spring Security verifies it
                .authorities(authorities)
                .accountExpired(false)
                .credentialsExpired(false)
                .disabled(user.getStatus() == User.UserStatus.INACTIVE
                        || user.getStatus() == User.UserStatus.SUSPENDED)
                .accountLocked(user.isAccountLocked())
                .build();
    }
}
