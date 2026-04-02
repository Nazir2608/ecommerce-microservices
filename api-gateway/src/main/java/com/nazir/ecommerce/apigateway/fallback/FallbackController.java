package com.nazir.ecommerce.apigateway.fallback;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Fallback controller — called when circuit breaker OPENS for a downstream service.
 * <p>
 * — What is a circuit breaker fallback?
 * Without fallback: client waits 30s for timeout → gateway returns 504 Gateway Timeout
 * With fallback:    circuit opens instantly → gateway returns 503 in milliseconds
 * <p>
 * The fallback is a graceful degradation response:
 * - Tells the client WHAT is unavailable and WHY
 * - Returns structured JSON (not an ugly 504 HTML error)
 * - Avoids cascading failures (downstream timeout doesn't block the gateway thread)
 * <p>
 * — Why /fallback/{service} not /error?
 * Each service gets its own fallback URL so we can return service-specific messages.
 * The circuit breaker config references: fallbackUri: forward:/fallback/user-service
 * Gateway internally forwards to this controller when the circuit opens.
 */
@RestController
@RequestMapping("/fallback")
@Slf4j
public class FallbackController {

    @GetMapping("/user-service")
    public ResponseEntity<Map<String, Object>> userServiceFallback() {
        return fallback("user-service", "Authentication and user management");
    }

    @GetMapping("/product-service")
    public ResponseEntity<Map<String, Object>> productServiceFallback() {
        return fallback("product-service", "Product catalog and inventory");
    }

    @GetMapping("/order-service")
    public ResponseEntity<Map<String, Object>> orderServiceFallback() {
        return fallback("order-service", "Order placement and management");
    }

    @GetMapping("/payment-service")
    public ResponseEntity<Map<String, Object>> paymentServiceFallback() {
        return fallback("payment-service", "Payment processing");
    }

    @GetMapping("/notification-service")
    public ResponseEntity<Map<String, Object>> notificationServiceFallback() {
        return fallback("notification-service", "Notifications");
    }

    private ResponseEntity<Map<String, Object>> fallback(String service, String description) {
        log.warn("[Gateway Fallback] Circuit open for service: {}", service);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                "success", false,
                "status", 503,
                "service", service,
                "description", description,
                "message", service + " is temporarily unavailable. Please try again in a moment.",
                "timestamp", LocalDateTime.now().toString(),
                "action", "The team has been notified. Retry in 10-30 seconds."
        ));
    }
}
