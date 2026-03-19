package com.nazir.ecommerce.userservice.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Stateless JWT authentication filter — runs on every HTTP request.
 *
 * LEARNING POINT — OncePerRequestFilter:
 *   Guaranteed to execute exactly once per request, even if a request
 *   is dispatched multiple times internally (e.g. forward/include).
 *
 * LEARNING POINT — filter execution order:
 *   Spring Security has a filter chain. This filter runs BEFORE
 *   the authorization check. If the JWT is valid, it sets the
 *   SecurityContext so the authorization check has the user's roles.
 *
 * LEARNING POINT — Token blacklist check:
 *   Even a valid JWT (correct signature, not expired) can be blacklisted
 *   in Redis after the user logged out. We check the blacklist on every request.
 *   The key is "blacklist:<token>" and it expires when the token would have expired.
 *
 * ┌──────────────────────────────────────────────────────────────────────┐
 * │  Request flow:                                                       │
 * │  Extract token → validate signature → check blacklist → set context  │
 * └──────────────────────────────────────────────────────────────────────┘
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BLACKLIST_PREFIX = "blacklist:";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;
    private final CustomUserDetailsService userDetailsService;
    private final StringRedisTemplate redisTemplate;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        // 1. Extract token from Authorization header
        String token = extractToken(request);

        if (token == null) {
            // No token — continue without authentication (public endpoints will pass, secured ones will 401)
            filterChain.doFilter(request, response);
            return;
        }

        // 2. Validate token signature and expiry
        if (!jwtTokenProvider.isValid(token)) {
            log.debug("JWT validation failed for request to {}", request.getRequestURI());
            filterChain.doFilter(request, response);
            return;
        }

        // 3. Reject refresh tokens used as access tokens
        if (jwtTokenProvider.isRefreshToken(token)) {
            log.warn("Refresh token used as access token — rejected");
            filterChain.doFilter(request, response);
            return;
        }

        // 4. Check if the token was blacklisted (e.g. user logged out)
        if (isBlacklisted(token)) {
            log.warn("Blacklisted token used for request to {}", request.getRequestURI());
            filterChain.doFilter(request, response);
            return;
        }

        // 5. Load user and set authentication in SecurityContext
        try {
            String email = jwtTokenProvider.extractEmail(token);

            // Only set auth if not already set (avoid redundant work)
            if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails = userDetailsService.loadUserByUsername(email);

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                userDetails,
                                null,
                                userDetails.getAuthorities()
                        );
                authentication.setDetails(
                        new WebAuthenticationDetailsSource().buildDetails(request)
                );

                // Set in SecurityContext — downstream code can call
                // SecurityContextHolder.getContext().getAuthentication() to get user info
                SecurityContextHolder.getContext().setAuthentication(authentication);
                log.debug("Authenticated user '{}' for URI: {}", email, request.getRequestURI());
            }
        } catch (Exception e) {
            log.error("Cannot set user authentication: {}", e.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    /** Extract "Bearer <token>" from Authorization header. Returns null if absent. */
    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }
        return null;
    }

    private boolean isBlacklisted(String token) {
        return Boolean.TRUE.equals(
                redisTemplate.hasKey(BLACKLIST_PREFIX + token)
        );
    }
}
