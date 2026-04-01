package com.nazir.ecommerce.paymentservice.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(title = "Payment Service API", version = "1.0",
                description = "Payment processing with idempotency"),
        servers = {
                @Server(url = "http://localhost:8083", description = "Local direct"),
                @Server(url = "http://localhost:8080", description = "Via API Gateway")
        }
)
public class OpenApiConfig {
}
