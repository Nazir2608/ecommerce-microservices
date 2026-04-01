package com.nazir.ecommerce.paymentservice.dto.request;

import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefundRequest {
    @NotNull
    @Positive
    private BigDecimal amount;
    @Size(max = 200)
    private String reason;
}
