package com.nazir.ecommerce.productservice.dto.response;

import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class StockResponse {
    private String productId;
    private String sku;
    private int stockQuantity;
    private int reservedQuantity;
    private int availableStock;
    private boolean inStock;
}
