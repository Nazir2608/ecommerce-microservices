package com.nazir.ecommerce.paymentservice.controller;

import com.nazir.ecommerce.paymentservice.dto.request.RefundRequest;
import com.nazir.ecommerce.paymentservice.dto.response.ApiResponse;
import com.nazir.ecommerce.paymentservice.dto.response.PaymentResponse;
import com.nazir.ecommerce.paymentservice.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Tag(name = "Payments", description = "Payment status and refund management")
public class PaymentController {

    private final PaymentService paymentService;

    @GetMapping("/order/{orderId}")
    @Operation(summary = "Get payment status for an order")
    public ResponseEntity<ApiResponse<PaymentResponse>> getByOrderId(@PathVariable UUID orderId) {
        return ResponseEntity.ok(ApiResponse.success(paymentService.getByOrderId(orderId)));
    }

    @GetMapping("/{paymentId}")
    @Operation(summary = "Get payment by ID")
    public ResponseEntity<ApiResponse<PaymentResponse>> getById(@PathVariable UUID paymentId) {
        return ResponseEntity.ok(ApiResponse.success(paymentService.getById(paymentId)));
    }

    @GetMapping("/my-payments")
    @Operation(summary = "Get payment history for authenticated user")
    public ResponseEntity<ApiResponse<Page<PaymentResponse>>> getMyPayments(@RequestHeader("X-Auth-User-Id") String userId, @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(paymentService.getByUser(UUID.fromString(userId), pageable)));
    }

    @PostMapping("/{paymentId}/refund")
    @Operation(summary = "Initiate refund for a successful payment — ADMIN only")
    public ResponseEntity<ApiResponse<PaymentResponse>> refund(@PathVariable UUID paymentId, @Valid @RequestBody RefundRequest request) {
        return ResponseEntity.ok(ApiResponse.success(paymentService.refund(paymentId, request), "Refund initiated"));
    }
}
