package com.nazir.ecommerce.serviceregistry;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Secures the Eureka server with HTTP Basic Authentication.
 * <p>
 * Why secure Eureka?
 * Without auth: any process on the network can register as "payment-service"
 * and receive traffic intended for the real payment-service. Security risk.
 * <p>
 * With Basic Auth: services authenticate using username/password.
 * Credentials are set in each service's eureka.client.serviceUrl.defaultZone:
 * http://eurekauser:eurekapassword@service-registry:8761/eureka
 * <p>
 * CSRF disabled for Eureka:
 * Eureka clients use non-browser HTTP calls (not forms).
 * CSRF protection is for browser-based form submissions.
 * Disabling it allows Eureka client REST calls to work correctly.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${eureka.username:eurekauser}")
    private String username;

    @Value("${eureka.password:eurekapassword}")
    private String password;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf
                        // Disable CSRF for Eureka REST endpoints (non-browser clients)
                        .ignoringRequestMatchers("/eureka/**")
                )
                .authorizeHttpRequests(auth -> auth
                        // Actuator health: allow unauthenticated (for Docker healthchecks)
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        // Prometheus metrics: allow from internal network
                        .requestMatchers("/actuator/prometheus").permitAll()
                        // Everything else requires authentication
                        .anyRequest().authenticated()
                )
                // Enable HTTP Basic Auth (username:password in header)
                .httpBasic(Customizer.withDefaults());

        return http.build();
    }

    @Bean
    public UserDetailsService userDetailsService(PasswordEncoder encoder) {
        var user = User.builder()
                .username(username)
                .password(encoder.encode(password))
                .roles("EUREKA_CLIENT")
                .build();

        return new InMemoryUserDetailsManager(user);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
