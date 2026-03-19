package com.nazir.ecommerce.userservice.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 3 (Swagger UI) configuration.
 * Access the UI at: http://localhost:8081/swagger-ui.html
 *
 * LEARNING POINT — @SecurityScheme:
 *   Tells Swagger UI to show a "Authorize" button where you paste a JWT.
 *   All endpoints annotated with @SecurityRequirement("bearerAuth") will
 *   automatically send "Authorization: Bearer <your-token>" in test requests.
 */
@Configuration
@OpenAPIDefinition(
    info = @Info(
        title       = "User Service API",
        version     = "1.0",
        description = "Authentication, registration, and user profile management",
        contact     = @Contact(name = "Nazir", email = "nazir@ecommerce.com")
    ),
    servers = {
        @Server(url = "http://localhost:8081", description = "Local direct"),
        @Server(url = "http://localhost:8080", description = "Via API Gateway")
    }
)
@SecurityScheme(
    name        = "bearerAuth",
    type        = SecuritySchemeType.HTTP,
    scheme      = "bearer",
    bearerFormat = "JWT",
    in          = SecuritySchemeIn.HEADER
)
public class OpenApiConfig {
    // All configuration is done via annotations above.
    // No bean methods needed for basic OpenAPI setup.
}
