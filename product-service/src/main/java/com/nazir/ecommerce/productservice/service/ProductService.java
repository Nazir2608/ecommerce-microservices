package com.nazir.ecommerce.productservice.service;

import com.nazir.ecommerce.productservice.dto.request.CreateProductRequest;
import com.nazir.ecommerce.productservice.dto.request.StockUpdateRequest;
import com.nazir.ecommerce.productservice.dto.request.UpdateProductRequest;
import com.nazir.ecommerce.productservice.dto.response.ProductResponse;
import com.nazir.ecommerce.productservice.dto.response.StockResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ProductService {

    ProductResponse create(CreateProductRequest request, String sellerId);

    ProductResponse getById(String id);

    ProductResponse getBySku(String sku);

    Page<ProductResponse> getAll(Pageable pageable);

    Page<ProductResponse> getByCategory(String category, Pageable pageable);

    Page<ProductResponse> search(String query, Pageable pageable);

    Page<ProductResponse> getBySeller(String sellerId, Pageable pageable);

    ProductResponse update(String id, UpdateProductRequest request);

    void delete(String id);

    // ── Stock operations (called by order-service via REST) ──────────────────

    StockResponse getStock(String productId);

    /**
     * Reserve N units for a pending order.
     * Increments reservedQuantity — does NOT decrement stockQuantity.
     * If insufficient available stock → throws InsufficientStockException.
     */
    void reserveStock(String productId, StockUpdateRequest request);

    /**
     * Release previously reserved stock.
     * Called when order is cancelled or payment fails.
     */
    void releaseStock(String productId, StockUpdateRequest request);

    /**
     * Permanently deduct stock after successful payment.
     * Decrements both stockQuantity AND reservedQuantity.
     */
    void confirmStockDeduction(String productId, StockUpdateRequest request);
}
