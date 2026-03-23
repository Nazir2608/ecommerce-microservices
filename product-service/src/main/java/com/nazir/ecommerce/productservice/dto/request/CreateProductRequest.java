package com.nazir.ecommerce.productservice.dto.request;

import jakarta.validation.constraints.*;
import lombok.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CreateProductRequest {

    @NotBlank(message = "Product name is required")
    @Size(max = 200)
    private String name;

    @Size(max = 2000)
    private String description;

    @Size(max = 100)
    private String brand;

    @NotBlank(message = "Category is required")
    @Size(max = 100)
    private String category;

    @NotNull(message = "Price is required")
    @Positive(message = "Price must be positive")
    @Digits(integer = 10, fraction = 2)
    private BigDecimal price;

    @Positive
    @Digits(integer = 10, fraction = 2)
    private BigDecimal compareAtPrice;

    @Size(max = 3, min = 3)
    private String currency;                 // ISO 4217: "USD", "INR", "EUR"

    /** Flexible product-type attributes — no schema restrictions */
    private Map<String, Object> attributes;

    private List<String> imageUrls;
    private List<String> tags;

    @NotNull(message = "Stock quantity is required")
    @Min(value = 0, message = "Stock cannot be negative")
    private Integer stockQuantity;
}
