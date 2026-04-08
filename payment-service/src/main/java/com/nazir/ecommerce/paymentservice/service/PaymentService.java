package com.nazir.ecommerce.paymentservice.service;

import com.nazir.ecommerce.paymentservice.dto.request.RefundRequest;
import com.nazir.ecommerce.paymentservice.dto.response.PaymentResponse;
import com.nazir.ecommerce.paymentservice.event.OrderEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface PaymentService {
    void processOrderPayment(OrderEvent event);  // called by @KafkaListener

    PaymentResponse getByOrderId(UUID orderId);

    PaymentResponse getById(UUID paymentId);

    Page<PaymentResponse> getByUser(UUID userId, Pageable pageable);

    PaymentResponse refund(UUID paymentId, RefundRequest request);
}
