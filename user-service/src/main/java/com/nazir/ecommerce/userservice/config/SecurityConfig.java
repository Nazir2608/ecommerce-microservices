package com.nazir.ecommerce.userservice.config;

import com.nazir.ecommerce.userservice.security.CustomUserDetailsService;
import com.nazir.ecommerce.userservice.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security 6 configuration.
 *
 * ┌──────────────────────────────────────────────────────────────────────────┐
 * │  LEARNING POINT — Spring Security 6 (Lambda DSL)                         │
 * │                                                                          │
 * │  Spring Security 5 used method chaining (.and().csrf()...).              │
 * │  Spring Security 6 uses Lambda DSL — cleaner, more readable.            │
 * │                                                                          │
 * │  Old (Security 5):                                                       │
 * │    http.csrf().disable()                                                 │
 * │       .sessionManagement().sessionCreationPolicy(STATELESS)             │
 * │                                                                          │
 * │  New (Security 6):                                                       │
 * │    http.csrf(AbstractHttpConfigurer::disable)                            │
 * │       .sessionManagement(s -> s.sessionCreationPolicy(STATELESS))       │
 * └──────────────────────────────────────────────────────────────────────────┘
 *
 * ┌──────────────────────────────────────────────────────────────────────────┐
 * │  LEARNING POINT — STATELESS session management                           │
 * │                                                                          │
 * │  REST APIs don't use HTTP sessions — every request is self-contained.   │
 * │  SessionCreationPolicy.STATELESS tells Spring Security:                 │
 * │    • Never create an HttpSession                                         │
 * │    • Never read SecurityContext from HttpSession                         │
 * │  Authentication state lives in the JWT, not the server.                 │
 * └──────────────────────────────────────────────────────────────────────────┘
 *
 * ┌──────────────────────────────────────────────────────────────────────────┐
 * │  LEARNING POINT — CSRF disabled for REST APIs                            │
 * │                                                                          │
 * │  CSRF attacks exploit browser auto-sending session cookies.              │
 * │  JWTs in Authorization headers are NOT auto-sent by browsers.           │
 * │  → CSRF protection is irrelevant (and would break our API) for JWT auth. │
 * └──────────────────────────────────────────────────────────────────────────┘
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity          // enables @PreAuthorize, @Secured, @RolesAllowed
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomUserDetailsService userDetailsService;
    private final JwtAuthenticationFilter jwtAuthFilter;

    // ─── Public endpoints (no JWT needed) ─────────────────────────────────────

    private static final String[] PUBLIC_POST = {
        "/api/v1/auth/register",
        "/api/v1/auth/login",
        "/api/v1/auth/refresh"
    };

    private static final String[] PUBLIC_GET = {
        "/swagger-ui.html",
        "/swagger-ui/**",
        "/v3/api-docs/**",
        "/actuator/health",
        "/actuator/info"
    };

    // ─── SecurityFilterChain ──────────────────────────────────────────────────

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
            // 1. CSRF — disabled (JWT in Authorization header, not cookie)
            .csrf(AbstractHttpConfigurer::disable)

            // 2. CORS — configured via CorsConfig bean (or use .cors(cors -> cors.configurationSource(...)))
            .cors(cors -> {})

            // 3. Session — STATELESS: no HttpSession, no cookies, pure JWT
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )

            // 4. Authorization rules
            .authorizeHttpRequests(auth -> auth
                // Public auth endpoints
                .requestMatchers(HttpMethod.POST, PUBLIC_POST).permitAll()
                // Swagger + actuator
                .requestMatchers(HttpMethod.GET, PUBLIC_GET).permitAll()
                // Admin-only endpoints
                .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                // All other requests require a valid JWT
                .anyRequest().authenticated()
            )

            // 5. Plug in our custom DaoAuthenticationProvider
            .authenticationProvider(authenticationProvider())

            // 6. Insert JWT filter BEFORE Spring's UsernamePasswordAuthenticationFilter
            //    so SecurityContext is populated before authorization checks run
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)

            .build();
    }

    // ─── Beans ────────────────────────────────────────────────────────────────

    /**
     * BCrypt password encoder.
     *
     * LEARNING POINT — BCrypt:
     *   BCrypt is adaptive — the cost factor (strength) can be increased over time
     *   to keep pace with hardware improvements. BCryptPasswordEncoder(12) means
     *   2^12 = 4096 iterations. Each hash takes ~250ms — fast enough for login,
     *   slow enough to make brute-force infeasible.
     *   Never use MD5 or SHA1 for passwords.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    /**
     * DaoAuthenticationProvider — ties together UserDetailsService and PasswordEncoder.
     * Spring Security calls this during AuthenticationManager.authenticate().
     */
    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    /**
     * Expose AuthenticationManager as a bean so AuthService can call
     * authManager.authenticate(new UsernamePasswordAuthenticationToken(email, password)).
     */
    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
