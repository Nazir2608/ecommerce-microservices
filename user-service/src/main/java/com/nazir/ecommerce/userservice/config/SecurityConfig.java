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
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity          // enables @PreAuthorize, @Secured, @RolesAllowed
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomUserDetailsService userDetailsService;
    private final JwtAuthenticationFilter jwtAuthFilter;

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

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                // 1. CSRF — disabled (JWT in Authorization header, not cookie)
                .csrf(AbstractHttpConfigurer::disable)

                // 2. CORS — configured via CorsConfig bean (or use .cors(cors -> cors.configurationSource(...)))
                .cors(cors -> {
                })

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
