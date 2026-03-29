package com.nazir.ecommerce.orderservice.client;

import com.nazir.ecommerce.orderservice.client.dto.ApiResponseWrapper;
import com.nazir.ecommerce.orderservice.client.dto.ProductInfo;
import com.nazir.ecommerce.orderservice.client.dto.StockRequest;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.*;

/**
 * Feign client for product-service.
 *
 * LEARNING POINT — How Feign + Eureka works together:
 *   name = "product-service" → Spring asks Eureka: "where is product-service?"
 *   Eureka returns: [{ host: 192.168.1.5, port: 8085 }]
 *   Spring Cloud LoadBalancer picks one (round-robin by default)
 *   Feign builds HTTP request → GET http://192.168.1.5:8085/api/v1/products/{id}
 *   Zero hardcoded URLs. New product-service instance → auto-discovered.
 *
 * LEARNING POINT — @CircuitBreaker + @Retry execution order:
 *   Request → @Retry (try up to 3x) → @CircuitBreaker (track failures)
 *   If all 3 retries fail → circuit breaker records 1 failure
 *   After 5 failures in 10-call window → circuit OPENS
 *   → fallback() called instantly (no more network calls)
 *   After 10s → HALF-OPEN: allows 3 test calls
 *   → success → CLOSED again
 */
@FeignClient(name = "product-service", fallback = ProductServiceClient.Fallback.class)
public interface ProductServiceClient {

    @GetMapping("/api/v1/products/{id}")
    @CircuitBreaker(name = "product-service")
    @Retry(name = "product-service")
    ApiResponseWrapper<ProductInfo> getProduct(@PathVariable("id") String productId);

    @PostMapping("/api/v1/products/{id}/reserve-stock")
    @CircuitBreaker(name = "product-service")
    @Retry(name = "product-service")
    void reserveStock(@PathVariable("id") String productId, @RequestBody StockRequest request);

    @PostMapping("/api/v1/products/{id}/release-stock")
    @CircuitBreaker(name = "product-service")
    void releaseStock(@PathVariable("id") String productId, @RequestBody StockRequest request);

    @PostMapping("/api/v1/products/{id}/confirm-stock")
    @CircuitBreaker(name = "product-service")
    void confirmStock(@PathVariable("id") String productId, @RequestBody StockRequest request);

    /**
     * Fallback bean — injected when circuit is OPEN or product-service is unreachable.
     *
     * LEARNING POINT — Graceful degradation:
     *   getProduct → return wrapper with null data (caller handles null check)
     *   reserveStock → throw exception (cannot create order without stock)
     *   releaseStock → log warning (best-effort; compensating transaction)
     *   confirmStock → log error  (requires manual reconciliation if this fails)
     */
    @Slf4j
    @Component
    class Fallback implements ProductServiceClient {

        @Override
        public ApiResponseWrapper<ProductInfo> getProduct(String productId) {
            log.warn("[CircuitBreaker OPEN] getProduct({}) — returning empty", productId);
            ApiResponseWrapper<ProductInfo> r = new ApiResponseWrapper<>();
            r.setSuccess(false);
            r.setMessage("product-service unavailable");
            return r;
        }

        @Override
        public void reserveStock(String productId, StockRequest request) {
            log.error("[CircuitBreaker OPEN] reserveStock({}) — CANNOT place order!", productId);
            throw new RuntimeException(
                "Product service is currently unavailable. Please try again in a few moments.");
        }

        @Override
        public void releaseStock(String productId, StockRequest request) {
            log.error("[CircuitBreaker OPEN] releaseStock({}) — stock reservation NOT released! orderId={}",
                productId, request.getOrderId());
        }

        @Override
        public void confirmStock(String productId, StockRequest request) {
            log.error("[CircuitBreaker OPEN] confirmStock({}) — stock deduction FAILED! orderId={}",
                productId, request.getOrderId());
        }
    }
}
