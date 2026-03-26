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

    void reserveStock(String productId, StockUpdateRequest request);

    void releaseStock(String productId, StockUpdateRequest request);

    void confirmStockDeduction(String productId, StockUpdateRequest request);
}
