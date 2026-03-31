package com.nazir.ecommerce.orderservice.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;
import java.math.BigDecimal;

/** Maps the 'data' field from product-service ApiResponse<ProductResponse> */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProductInfo {
    private String id;
    private String name;
    private String sku;
    private String category;
    private BigDecimal price;
    private int availableStock;
    private boolean inStock;
    private String status;
}
