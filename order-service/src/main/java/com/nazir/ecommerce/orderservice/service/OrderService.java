package com.nazir.ecommerce.orderservice.service;

import com.nazir.ecommerce.orderservice.dto.request.PlaceOrderRequest;
import com.nazir.ecommerce.orderservice.dto.response.OrderResponse;
import com.nazir.ecommerce.orderservice.event.PaymentEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface OrderService {
    OrderResponse placeOrder(PlaceOrderRequest request, UUID userId, String userEmail);
    OrderResponse getById(UUID orderId, UUID userId);
    OrderResponse getByIdAdmin(UUID orderId);
    List<OrderResponse> getMyOrders(UUID userId);
    Page<OrderResponse> getAllOrders(Pageable pageable);
    OrderResponse cancelOrder(UUID orderId, UUID userId, String reason);
    OrderResponse shipOrder(UUID orderId);
    OrderResponse deliverOrder(UUID orderId);
    void handlePaymentEvent(PaymentEvent event);   // called by @KafkaListener
}
