package com.nazir.ecommerce.paymentservice.gateway;

import lombok.*;

/**
 * Gateway result — decoupled from any specific provider.
 * Stripe returns JSON, PayPal returns XML — both map to this.
 */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PaymentGatewayResult {
    private boolean success;
    private String  transactionId;
    private String  rawResponse;
    private String  failureReason;
    private String  errorCode;
}
