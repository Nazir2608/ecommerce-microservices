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

/**
 * Product — MongoDB document.
 *
 * LEARNING POINT — @Document vs @Entity:
 *   @Document → MongoDB collection (schema-free, no migrations needed)
 *   @Entity   → SQL table (strict schema, migration required on change)
 *
 * LEARNING POINT — Map<String, Object> attributes:
 *   This single field stores ALL product-type-specific attributes.
 *   A laptop document stores: {"ram":"16GB","cpu":"M3","storage":"512GB"}
 *   A t-shirt document stores: {"sizes":["S","M","L"],"color":"blue"}
 *   No schema change needed to add a new product type.
 *
 * LEARNING POINT — Serializable for Redis:
 *   ProductResponse (mapped from this) must be Serializable so Redis
 *   can serialize/deserialize it. We implement Serializable here as well
 *   for safety if the document itself is ever cached.
 *
 * LEARNING POINT — @Version (Optimistic Locking):
 *   Prevents two concurrent requests from overwriting each other's stock changes.
 *   If request A and B both read version=1 and try to save, the second save
 *   gets an OptimisticLockingFailureException and must retry.
 *   Critical for the reserveStock method.
 */
@Document(collection = "products")
@CompoundIndex(name = "idx_category_status", def = "{'category': 1, 'status': 1}")
@CompoundIndex(name = "idx_category_price",  def = "{'category': 1, 'price': 1}")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@ToString(exclude = {"attributes", "imageUrls"})
@EqualsAndHashCode(of = "id")
public class Product implements Serializable {

    @Id
    private String id;

    @Indexed(unique = true)
    private String sku;                    // Stock-keeping unit: "LAP-MBP-20240101"

    @TextIndexed(weight = 3)               // Higher weight → stronger text search match
    private String name;

    @TextIndexed(weight = 1)
    private String description;

    @TextIndexed(weight = 2)
    private String brand;

    @Indexed
    private String category;              // "laptops", "phones", "clothing"

    private BigDecimal price;
    private BigDecimal compareAtPrice;    // Original price for showing discounts
    private String currency;

    /**
     * Flexible product attributes — the core MongoDB advantage.
     * Stored as a nested BSON document. Never add these to relational columns.
     */
    private Map<String, Object> attributes;

    private List<String> imageUrls;
    private List<String> tags;

    private Integer stockQuantity;
    private Integer reservedQuantity;     // Held by pending orders (not yet paid)

    @Indexed
    private ProductStatus status;

    private Double averageRating;
    private Integer reviewCount;

    private String sellerId;              // Denormalized from user-service

    /**
     * LEARNING POINT — @Version for optimistic locking in MongoDB.
     * Spring Data MongoDB increments this on every save.
     * Concurrent saves with same version number → exception (retry required).
     */
    @Version
    private Long version;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    // ─── Domain logic ────────────────────────────────────────────────────────

    /** Available stock = total - reserved (for pending orders) */
    public int getAvailableStock() {
        int stock    = stockQuantity    != null ? stockQuantity    : 0;
        int reserved = reservedQuantity != null ? reservedQuantity : 0;
        return Math.max(0, stock - reserved);
    }

    public boolean isInStock() {
        return getAvailableStock() > 0;
    }

    // ─── Status enum ─────────────────────────────────────────────────────────

    public enum ProductStatus {
        ACTIVE,          // visible in catalog
        INACTIVE,        // hidden by seller
        OUT_OF_STOCK,    // auto-set when stockQuantity = 0
        DISCONTINUED     // soft-deleted
    }
}
