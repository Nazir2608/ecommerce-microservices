package com.nazir.ecommerce.orderservice.controller;

import com.nazir.ecommerce.orderservice.dto.request.PlaceOrderRequest;
import com.nazir.ecommerce.orderservice.dto.response.ApiResponse;
import com.nazir.ecommerce.orderservice.dto.response.OrderResponse;
import com.nazir.ecommerce.orderservice.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Order REST API controller.
 *
 *   Header-based user identity:
 *   The API Gateway validates the JWT, extracts userId + email, and forwards
 *   them as HTTP headers: X-Auth-User-Id, X-Auth-User-Email.
 *   This service TRUSTS those headers — it never validates the JWT itself.
 *   This avoids each service needing to duplicate JWT validation logic.
 *   Security model: trust the gateway, not the client.
 */
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
@Tag(name = "Orders", description = "Order placement and lifecycle management")
public class OrderController {

    private final OrderService orderService;


    @PostMapping
    @Operation(summary = "Place a new order — validates stock via Feign → product-service")
    public ResponseEntity<ApiResponse<OrderResponse>> placeOrder(
            @Valid @RequestBody PlaceOrderRequest request,
            @RequestHeader("X-Auth-User-Id")    String userId,
            @RequestHeader("X-Auth-User-Email") String userEmail) {

        OrderResponse response = orderService.placeOrder(request, UUID.fromString(userId), userEmail);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response, "Order placed successfully"));
    }

    @GetMapping("/my-orders")
    @Operation(summary = "Get all orders for the authenticated user")
    public ResponseEntity<ApiResponse<List<OrderResponse>>> getMyOrders(@RequestHeader("X-Auth-User-Id") String userId) {
        return ResponseEntity.ok(ApiResponse.success(orderService.getMyOrders(UUID.fromString(userId))));
    }

    @GetMapping("/{orderId}")
    @Operation(summary = "Get order by ID (customer can only see their own orders)")
    public ResponseEntity<ApiResponse<OrderResponse>> getById(@PathVariable UUID orderId, @RequestHeader("X-Auth-User-Id") String userId) {
        return ResponseEntity.ok(ApiResponse.success(orderService.getById(orderId, UUID.fromString(userId))));
    }

    @PostMapping("/{orderId}/cancel")
    @Operation(summary = "Cancel order (only PENDING or CONFIRMED orders)")
    public ResponseEntity<ApiResponse<OrderResponse>> cancel(
            @PathVariable UUID orderId,
            @RequestHeader("X-Auth-User-Id") String userId,
            @RequestBody(required = false) Map<String, String> body) {

        String reason = body != null ? body.get("reason") : null;
        return ResponseEntity.ok(ApiResponse.success(orderService.cancelOrder(orderId, UUID.fromString(userId), reason), "Order cancelled"));
    }

    // ── Admin / Warehouse endpoints ─────────

    @GetMapping
    @Operation(summary = "List all orders — ADMIN only")
    public ResponseEntity<ApiResponse<Page<OrderResponse>>> getAllOrders(@PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(orderService.getAllOrders(pageable)));
    }

    @GetMapping("/admin/{orderId}")
    @Operation(summary = "Get any order by ID — ADMIN only")
    public ResponseEntity<ApiResponse<OrderResponse>> getByIdAdmin(@PathVariable UUID orderId) {
        return ResponseEntity.ok(ApiResponse.success(orderService.getByIdAdmin(orderId)));
    }

    @PostMapping("/{orderId}/ship")
    @Operation(summary = "Mark order as shipped — WAREHOUSE/ADMIN only")
    public ResponseEntity<ApiResponse<OrderResponse>> ship(@PathVariable UUID orderId) {
        return ResponseEntity.ok(ApiResponse.success(orderService.shipOrder(orderId), "Order marked as shipped"));
    }

    @PostMapping("/{orderId}/deliver")
    @Operation(summary = "Mark order as delivered — WAREHOUSE/ADMIN only")
    public ResponseEntity<ApiResponse<OrderResponse>> deliver(@PathVariable UUID orderId) {
        return ResponseEntity.ok(ApiResponse.success(orderService.deliverOrder(orderId), "Order marked as delivered"));
    }
}
