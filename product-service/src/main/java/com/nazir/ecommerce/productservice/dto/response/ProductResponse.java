package com.nazir.ecommerce.productservice.dto.response;

import com.nazir.ecommerce.productservice.model.Product;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductResponse implements Serializable {

    private String id;
    private String sku;
    private String name;
    private String description;
    private String brand;
    private String category;
    private BigDecimal price;
    private BigDecimal compareAtPrice;
    private String currency;
    private Map<String, Object> attributes;
    private List<String> imageUrls;
    private List<String> tags;
    private int availableStock;
    private boolean inStock;
    private Product.ProductStatus status;
    private Double averageRating;
    private Integer reviewCount;
    private String sellerId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
