package com.nazir.ecommerce.orderservice.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
    info    = @Info(title = "Order Service API", version = "1.0",
                    description = "Order placement, lifecycle, Saga pattern"),
    servers = {
        @Server(url = "http://localhost:8082", description = "Local direct"),
        @Server(url = "http://localhost:8080", description = "Via API Gateway")
    }
)
public class OpenApiConfig {}
