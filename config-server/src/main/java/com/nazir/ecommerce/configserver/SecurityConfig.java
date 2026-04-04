package com.nazir.ecommerce.configserver;

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
 * Config Server Basic Auth security.
 *
 * LEARNING POINT — Why secure the config server?
 *   Config files contain database passwords, JWT secrets, API keys.
 *   Without auth: any process can call GET /user-service/docker
 *                 and receive all credentials in plaintext.
 *   With Basic Auth: only services with the correct username:password can fetch config.
 *
 *   In docker-compose: CONFIG_SERVER_URI=http://configuser:configpassword@config-server:8888
 *   This embeds credentials in the bootstrap URL.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${config.server.username:configuser}")
    private String username;

    @Value("${config.server.password:configpassword}")
    private String password;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                .requestMatchers("/actuator/prometheus").permitAll()
                .anyRequest().authenticated()
            )
            .httpBasic(Customizer.withDefaults());
        return http.build();
    }

    @Bean
    public UserDetailsService userDetailsService(PasswordEncoder encoder) {
        var user = User.builder()
                .username(username)
                .password(encoder.encode(password))
                .roles("CONFIG_CLIENT")
                .build();
        return new InMemoryUserDetailsManager(user);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
