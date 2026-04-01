package com.nazir.ecommerce.paymentservice.gateway;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Random;
import java.util.UUID;

/**
 * Mock gateway — simulates a real payment provider.
 * <p>
 * LEARNING POINT — Why a mock gateway?
 * Real gateways (Stripe, PayPal, Razorpay) require:
 * • API keys and merchant accounts
 * • HTTPS with valid certificates
 * • Test credit card numbers
 * For local dev and CI, a mock lets us test the full flow without real money.
 * <p>
 * Mock behaviour:
 * • 90% success rate (random)
 * • Specific amounts trigger specific failure codes (testability)
 * • Simulates 200–500ms network latency
 * • Returns realistic transactionId format
 *
 * @Primary → Spring injects this when PaymentGateway is autowired.
 * To use Stripe: create StripeGateway implements PaymentGateway,
 * add @ConditionalOnProperty(name = "payment.gateway", havingValue = "stripe")
 */
@Component
@Primary
@Slf4j
public class MockPaymentGateway implements PaymentGateway {

    private final Random random = new Random();

    @Override
    public PaymentGatewayResult charge(UUID orderId, BigDecimal amount,
                                       String currency, String paymentMethod) {
        log.info("[MockGateway] Charging {} {} for orderId={}", amount, currency, orderId);

        // Simulate network latency
        simulateLatency();

        // Special amounts trigger specific results for testing:
        //   amount = 0.01 → always fail (insufficient funds)
        //   amount = 0.02 → always fail (card declined)
        if (amount.compareTo(new BigDecimal("0.01")) == 0) {
            return PaymentGatewayResult.builder()
                    .success(false).failureReason("Insufficient funds")
                    .errorCode("INSUFFICIENT_FUNDS").rawResponse("{\"error\":\"insufficient_funds\"}")
                    .build();
        }
        if (amount.compareTo(new BigDecimal("0.02")) == 0) {
            return PaymentGatewayResult.builder()
                    .success(false).failureReason("Card declined")
                    .errorCode("CARD_DECLINED").rawResponse("{\"error\":\"card_declined\"}")
                    .build();
        }

        // 90% success rate
        if (random.nextInt(100) < 90) {
            String txnId = "TXN-" + UUID.randomUUID().toString().replace("-", "").toUpperCase().substring(0, 16);
            log.info("[MockGateway] Payment SUCCESS transactionId={}", txnId);
            return PaymentGatewayResult.builder()
                    .success(true).transactionId(txnId)
                    .rawResponse("{\"status\":\"succeeded\",\"id\":\"" + txnId + "\",\"created\":" + Instant.now().getEpochSecond() + "}")
                    .build();
        } else {
            log.warn("[MockGateway] Payment FAILED (simulated)");
            return PaymentGatewayResult.builder()
                    .success(false).failureReason("Payment declined by issuer")
                    .errorCode("DO_NOT_HONOR").rawResponse("{\"error\":\"do_not_honor\"}")
                    .build();
        }
    }

    @Override
    public PaymentGatewayResult refund(String transactionId, BigDecimal amount) {
        log.info("[MockGateway] Refunding {} for txn={}", amount, transactionId);
        simulateLatency();
        String refundId = "REF-" + UUID.randomUUID().toString().replace("-", "").toUpperCase().substring(0, 12);
        return PaymentGatewayResult.builder()
                .success(true).transactionId(refundId)
                .rawResponse("{\"refund_id\":\"" + refundId + "\",\"status\":\"succeeded\"}")
                .build();
    }

    private void simulateLatency() {
        try {
            Thread.sleep(200 + random.nextInt(300)); // 200–500ms
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
