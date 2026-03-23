package com.nazir.ecommerce.productservice.model;

import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.index.TextIndexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Document(collection = "products")
@CompoundIndex(name = "idx_category_status", def = "{'category': 1, 'status': 1}")
@CompoundIndex(name = "idx_category_price", def = "{'category': 1, 'price': 1}")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"attributes", "imageUrls"})
@EqualsAndHashCode(of = "id")
public class Product implements Serializable {

    @Id
    private String id;

    @Indexed(unique = true)
    private String sku;

    @TextIndexed(weight = 3)
    private String name;

    @TextIndexed(weight = 1)
    private String description;

    @TextIndexed(weight = 2)
    private String brand;

    @Indexed
    private String category;

    private BigDecimal price;
    private BigDecimal compareAtPrice;
    private String currency;

    private Map<String, Object> attributes;

    private List<String> imageUrls;
    private List<String> tags;

    private Integer stockQuantity;
    private Integer reservedQuantity;

    @Indexed
    private ProductStatus status;

    private Double averageRating;
    private Integer reviewCount;

    private String sellerId;

    @Version
    private Long version;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;


    /**
     * Available stock = total - reserved (for pending orders)
     */
    public int getAvailableStock() {
        int stock = stockQuantity != null ? stockQuantity : 0;
        int reserved = reservedQuantity != null ? reservedQuantity : 0;
        return Math.max(0, stock - reserved);
    }

    public boolean isInStock() {
        return getAvailableStock() > 0;
    }


    public enum ProductStatus {
        ACTIVE,          // visible in catalog
        INACTIVE,        // hidden by seller
        OUT_OF_STOCK,    // auto-set when stockQuantity = 0
        DISCONTINUED     // soft-deleted
    }
}
