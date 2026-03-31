package com.nazir.ecommerce.orderservice.client.dto;

import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class StockRequest {
    private Integer quantity;
    private String  orderId;
}
