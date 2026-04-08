package com.nazir.ecommerce.paymentservice.gateway;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Payment gateway interface.
 * <p>
 * Interface-based abstraction (Strategy Pattern):
 * Today: MockPaymentGateway (90% success simulation)
 * Tomorrow: StripeGateway, RazorpayGateway
 * Day after: both, A/B tested
 * <p>
 * Switching gateway = swap the @Primary bean.
 * Zero changes to PaymentServiceImpl.
 * This is the Open/Closed principle: open for extension, closed for modification.
 */
public interface PaymentGateway {
    PaymentGatewayResult charge(UUID orderId, BigDecimal amount, String currency, String paymentMethod);
    PaymentGatewayResult refund(String transactionId, BigDecimal amount);
}
