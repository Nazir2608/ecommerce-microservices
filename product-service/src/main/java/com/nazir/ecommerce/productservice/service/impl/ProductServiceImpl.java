package com.nazir.ecommerce.productservice.service.impl;

import com.nazir.ecommerce.productservice.dto.request.CreateProductRequest;
import com.nazir.ecommerce.productservice.dto.request.StockUpdateRequest;
import com.nazir.ecommerce.productservice.dto.request.UpdateProductRequest;
import com.nazir.ecommerce.productservice.dto.response.ProductResponse;
import com.nazir.ecommerce.productservice.dto.response.StockResponse;
import com.nazir.ecommerce.productservice.event.StockEvent;
import com.nazir.ecommerce.productservice.event.StockEventPublisher;
import com.nazir.ecommerce.productservice.exception.DuplicateSkuException;
import com.nazir.ecommerce.productservice.exception.InsufficientStockException;
import com.nazir.ecommerce.productservice.exception.ProductNotFoundException;
import com.nazir.ecommerce.productservice.mapper.ProductMapper;
import com.nazir.ecommerce.productservice.model.Product;
import com.nazir.ecommerce.productservice.repository.ProductRepository;
import com.nazir.ecommerce.productservice.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Product service — demonstrates Cache-Aside pattern with Spring Cache + Redis.
 *
 * ┌──────────────────────────────────────────────────────────────────────────┐
 * │  LEARNING POINT — Cache-Aside (Lazy Loading) Pattern                     │
 * │                                                                          │
 * │  1. READ:  check cache → HIT: return fast   MISS: query DB → cache → return│
 * │  2. WRITE: update DB → update/evict cache                               │
 * │                                                                          │
 * │  @Cacheable("products")        → read-through                           │
 * │  @CachePut("products")         → write-through (always refresh cache)   │
 * │  @CacheEvict("products")       → evict on delete                        │
 * │  @Caching(evict = {...})       → evict multiple cache entries at once   │
 * └──────────────────────────────────────────────────────────────────────────┘
 *
 * ┌──────────────────────────────────────────────────────────────────────────┐
 * │  LEARNING POINT — Cache key design                                       │
 * │                                                                          │
 * │  "products"         → single product by ID                              │
 * │  "products_by_sku"  → single product by SKU                             │
 * │  "products_list"    → paginated lists (evicted on any write)            │
 * │                                                                          │
 * │  Cache TTL = 10 minutes (configured in CacheConfig).                    │
 * │  Stock quantities NOT cached because they change too frequently.        │
 * └──────────────────────────────────────────────────────────────────────────┘
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProductServiceImpl implements ProductService {

    private final ProductRepository    productRepository;
    private final ProductMapper        productMapper;
    private final StockEventPublisher  eventPublisher;

    // ─── Create ───────────────────────────────────────────────────────────────

    @Override
    @CacheEvict(value = "products_list", allEntries = true)
    public ProductResponse create(CreateProductRequest request, String sellerId) {
        String sku = generateSku(request.getCategory(), request.getName());

        if (productRepository.existsBySku(sku)) {
            throw new DuplicateSkuException("Product with SKU already exists: " + sku);
        }

        Product product = productMapper.toEntity(request);
        product.setSku(sku);
        product.setSellerId(sellerId);
        product.setStatus(Product.ProductStatus.ACTIVE);
        product.setReservedQuantity(0);
        product.setAverageRating(0.0);
        product.setReviewCount(0);

        if (product.getCurrency() == null) product.setCurrency("USD");

        Product saved = productRepository.save(product);
        log.info("Product created: id={}, sku={}, category={}", saved.getId(), saved.getSku(), saved.getCategory());

        return productMapper.toResponse(saved);
    }

    // ─── Read ─────────────────────────────────────────────────────────────────

    /**
     * LEARNING POINT — @Cacheable:
     *   First call → cache miss → queries MongoDB → stores in Redis with TTL.
     *   Subsequent calls → cache HIT → returned from Redis, MongoDB NOT queried.
     *   unless = "#result == null" → don't cache null results.
     */
    @Override
    @Cacheable(value = "products", key = "#id", unless = "#result == null")
    public ProductResponse getById(String id) {
        log.debug("Cache MISS for product id={}, querying MongoDB", id);
        return productRepository.findById(id)
                .map(productMapper::toResponse)
                .orElseThrow(() -> new ProductNotFoundException("Product not found: " + id));
    }

    @Override
    @Cacheable(value = "products_by_sku", key = "#sku", unless = "#result == null")
    public ProductResponse getBySku(String sku) {
        return productRepository.findBySku(sku)
                .map(productMapper::toResponse)
                .orElseThrow(() -> new ProductNotFoundException("Product not found with SKU: " + sku));
    }

    @Override
    @Cacheable(value = "products_list", key = "'all_' + #pageable.pageNumber + '_' + #pageable.pageSize")
    public Page<ProductResponse> getAll(Pageable pageable) {
        return productRepository.findByStatus(Product.ProductStatus.ACTIVE, pageable)
                .map(productMapper::toResponse);
    }

    @Override
    @Cacheable(value = "products_list",
               key = "#category + '_' + #pageable.pageNumber + '_' + #pageable.pageSize")
    public Page<ProductResponse> getByCategory(String category, Pageable pageable) {
        return productRepository.findByCategoryAndStatus(
                category, Product.ProductStatus.ACTIVE, pageable)
                .map(productMapper::toResponse);
    }

    @Override
    // Search not cached — too many unique query permutations would pollute Redis
    public Page<ProductResponse> search(String query, Pageable pageable) {
        return productRepository.searchActive(query, pageable)
                .map(productMapper::toResponse);
    }

    @Override
    public Page<ProductResponse> getBySeller(String sellerId, Pageable pageable) {
        return productRepository.findBySellerIdAndStatus(
                sellerId, Product.ProductStatus.ACTIVE, pageable)
                .map(productMapper::toResponse);
    }

    // ─── Update ───────────────────────────────────────────────────────────────

    /**
     * LEARNING POINT — @CachePut:
     *   Unlike @Cacheable, @CachePut ALWAYS executes the method and updates the cache.
     *   Use it for writes so the cache reflects the new state immediately.
     *   @Caching lets us evict multiple cache names in one annotation.
     */
    @Override
    @Caching(
        put    = { @CachePut(value = "products", key = "#result.id") },
        evict  = { @CacheEvict(value = "products_list",   allEntries = true),
                   @CacheEvict(value = "products_by_sku", key = "#result.sku") }
    )
    public ProductResponse update(String id, UpdateProductRequest request) {
        Product product = findEntityById(id);
        productMapper.updateEntity(request, product);
        Product saved = productRepository.save(product);
        log.info("Product updated: id={}", id);
        return productMapper.toResponse(saved);
    }

    // ─── Delete ───────────────────────────────────────────────────────────────

    /**
     * Soft delete — sets status to DISCONTINUED.
     * Never physically delete products (order history must remain accurate).
     */
    @Override
    @Caching(evict = {
        @CacheEvict(value = "products",      key = "#id"),
        @CacheEvict(value = "products_list", allEntries = true)
    })
    public void delete(String id) {
        Product product = findEntityById(id);
        product.setStatus(Product.ProductStatus.DISCONTINUED);
        productRepository.save(product);
        log.info("Product soft-deleted: id={}", id);
    }

    // ─── Stock operations ─────────────────────────────────────────────────────

    @Override
    // Stock NOT cached — changes too frequently
    public StockResponse getStock(String productId) {
        Product p = findEntityById(productId);
        return StockResponse.builder()
                .productId(p.getId())
                .sku(p.getSku())
                .stockQuantity(p.getStockQuantity() != null ? p.getStockQuantity() : 0)
                .reservedQuantity(p.getReservedQuantity() != null ? p.getReservedQuantity() : 0)
                .availableStock(p.getAvailableStock())
                .inStock(p.isInStock())
                .build();
    }

    /**
     * Reserve stock for a pending order.
     *
     * LEARNING POINT — Stock reservation pattern:
     *   We DON'T decrement stockQuantity here. We only increment reservedQuantity.
     *   availableStock = stockQuantity - reservedQuantity
     *   This way stock is "held" for the order but not permanently gone yet.
     *   If payment fails → releaseStock() frees the reservation.
     *   If payment succeeds → confirmStockDeduction() permanently deducts.
     *
     * LEARNING POINT — @CacheEvict on stock operations:
     *   We evict the product cache so subsequent getById() returns fresh stock data.
     */
    @Override
    @CacheEvict(value = "products", key = "#productId")
    public synchronized void reserveStock(String productId, StockUpdateRequest request) {
        Product product = findEntityById(productId);

        if (product.getAvailableStock() < request.getQuantity()) {
            throw new InsufficientStockException(
                    String.format("Insufficient stock for product '%s'. Available: %d, Requested: %d",
                            product.getName(), product.getAvailableStock(), request.getQuantity()));
        }

        int prev = product.getReservedQuantity() != null ? product.getReservedQuantity() : 0;
        product.setReservedQuantity(prev + request.getQuantity());
        productRepository.save(product);

        log.info("Stock reserved: productId={}, qty={}, orderId={}",
                productId, request.getQuantity(), request.getOrderId());

        eventPublisher.publish(StockEvent.builder()
                .eventType(StockEvent.EventType.STOCK_RESERVED)
                .productId(product.getId())
                .sku(product.getSku())
                .orderId(request.getOrderId())
                .quantity(request.getQuantity())
                .remainingStock(product.getAvailableStock())
                .build());
    }

    @Override
    @CacheEvict(value = "products", key = "#productId")
    public void releaseStock(String productId, StockUpdateRequest request) {
        Product product = findEntityById(productId);
        int prev = product.getReservedQuantity() != null ? product.getReservedQuantity() : 0;
        product.setReservedQuantity(Math.max(0, prev - request.getQuantity()));
        productRepository.save(product);

        log.info("Stock released: productId={}, qty={}, orderId={}",
                productId, request.getQuantity(), request.getOrderId());

        eventPublisher.publish(StockEvent.builder()
                .eventType(StockEvent.EventType.STOCK_RELEASED)
                .productId(product.getId())
                .sku(product.getSku())
                .orderId(request.getOrderId())
                .quantity(request.getQuantity())
                .remainingStock(product.getAvailableStock())
                .build());
    }

    @Override
    @CacheEvict(value = "products", key = "#productId")
    public void confirmStockDeduction(String productId, StockUpdateRequest request) {
        Product product = findEntityById(productId);

        int prevStock    = product.getStockQuantity()    != null ? product.getStockQuantity()    : 0;
        int prevReserved = product.getReservedQuantity() != null ? product.getReservedQuantity() : 0;

        product.setStockQuantity(Math.max(0, prevStock - request.getQuantity()));
        product.setReservedQuantity(Math.max(0, prevReserved - request.getQuantity()));

        // Auto-transition to OUT_OF_STOCK
        if (product.getStockQuantity() <= 0) {
            product.setStatus(Product.ProductStatus.OUT_OF_STOCK);
            eventPublisher.publish(StockEvent.builder()
                    .eventType(StockEvent.EventType.OUT_OF_STOCK)
                    .productId(product.getId())
                    .sku(product.getSku())
                    .quantity(0)
                    .remainingStock(0)
                    .build());
        }

        productRepository.save(product);
        log.info("Stock confirmed deducted: productId={}, qty={}", productId, request.getQuantity());

        eventPublisher.publish(StockEvent.builder()
                .eventType(StockEvent.EventType.STOCK_CONFIRMED)
                .productId(product.getId())
                .sku(product.getSku())
                .orderId(request.getOrderId())
                .quantity(request.getQuantity())
                .remainingStock(product.getAvailableStock())
                .build());
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    private Product findEntityById(String id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException("Product not found: " + id));
    }

    private String generateSku(String category, String name) {
        String cat = category.toUpperCase().replaceAll("[^A-Z]", "");
        cat = cat.substring(0, Math.min(3, cat.length()));
        String nm = name.toUpperCase().replaceAll("[^A-Z]", "");
        nm = nm.substring(0, Math.min(3, nm.length()));
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmm"));
        return cat + "-" + nm + "-" + ts;
    }
}
