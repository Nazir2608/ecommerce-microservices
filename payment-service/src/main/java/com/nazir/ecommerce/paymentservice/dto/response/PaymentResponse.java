package com.nazir.ecommerce.paymentservice.dto.response;

import com.nazir.ecommerce.paymentservice.model.Payment;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentResponse {
    private UUID id;
    private UUID orderId;
    private UUID userId;
    private BigDecimal amount;
    private String currency;
    private Payment.PaymentStatus status;
    private Payment.PaymentMethod paymentMethod;
    private String transactionId;
    private String failureReason;
    private LocalDateTime createdAt;
    private LocalDateTime processedAt;
}
