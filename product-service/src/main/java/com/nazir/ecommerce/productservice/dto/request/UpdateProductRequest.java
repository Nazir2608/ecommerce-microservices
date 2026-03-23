package com.nazir.ecommerce.productservice.dto.request;

import com.nazir.ecommerce.productservice.model.Product;
import jakarta.validation.constraints.*;
import lombok.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** All fields optional — service applies only non-null fields (partial update). */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class UpdateProductRequest {

    @Size(max = 200)
    private String name;

    @Size(max = 2000)
    private String description;

    @Positive
    @Digits(integer = 10, fraction = 2)
    private BigDecimal price;

    @Positive
    @Digits(integer = 10, fraction = 2)
    private BigDecimal compareAtPrice;

    private Map<String, Object> attributes;
    private List<String> imageUrls;
    private List<String> tags;

    private Product.ProductStatus status;
}
