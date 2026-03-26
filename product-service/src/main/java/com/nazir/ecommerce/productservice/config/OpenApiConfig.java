package com.nazir.ecommerce.productservice.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(title = "Product Service API", version = "1.0",
                description = "Product catalog, search, and stock management"),
        servers = {
                @Server(url = "http://localhost:8085", description = "Local direct"),
                @Server(url = "http://localhost:8080", description = "Via API Gateway")
        }
)
public class OpenApiConfig {
}
