package com.nazir.ecommerce.productservice.controller;

import com.nazir.ecommerce.productservice.dto.request.CreateProductRequest;
import com.nazir.ecommerce.productservice.dto.request.StockUpdateRequest;
import com.nazir.ecommerce.productservice.dto.request.UpdateProductRequest;
import com.nazir.ecommerce.productservice.dto.response.ApiResponse;
import com.nazir.ecommerce.productservice.dto.response.ProductResponse;
import com.nazir.ecommerce.productservice.dto.response.StockResponse;
import com.nazir.ecommerce.productservice.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Product REST controller.
 * <p>
 * Header-based auth propagation:
 * The API Gateway validates the JWT and forwards X-Auth-User-Id.
 * This service trusts that header — it never validates the JWT itself.
 * This is the standard microservices pattern: auth at the perimeter.
 */
@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
@Tag(name = "Products", description = "Product catalog management")
public class ProductController {

    private final ProductService productService;

    @GetMapping("/{id}")
    @Operation(summary = "Get product by ID (cached in Redis)")
    public ResponseEntity<ApiResponse<ProductResponse>> getById(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.success(productService.getById(id)));
    }

    @GetMapping("/sku/{sku}")
    @Operation(summary = "Get product by SKU")
    public ResponseEntity<ApiResponse<ProductResponse>> getBySku(@PathVariable String sku) {
        return ResponseEntity.ok(ApiResponse.success(productService.getBySku(sku)));
    }

    @GetMapping
    @Operation(summary = "List active products (paginated, optionally filtered by category)")
    public ResponseEntity<ApiResponse<Page<ProductResponse>>> list(@RequestParam(required = false) String category, @RequestParam(required = false) String search, @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        Page<ProductResponse> page;
        if (search != null && !search.isBlank()) {
            page = productService.search(search, pageable);
        } else if (category != null && !category.isBlank()) {
            page = productService.getByCategory(category, pageable);
        } else {
            page = productService.getAll(pageable);
        }
        return ResponseEntity.ok(ApiResponse.success(page));
    }

    // ── Authenticated endpoints (require X-Auth-User-Id from gateway) ────────

    @PostMapping
    @Operation(summary = "Create product (SELLER/ADMIN)")
    public ResponseEntity<ApiResponse<ProductResponse>> create(@Valid @RequestBody CreateProductRequest request, @RequestHeader("X-Auth-User-Id") String sellerId) {
        ProductResponse response = productService.create(request, sellerId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Product created"));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update product (SELLER/ADMIN)")
    public ResponseEntity<ApiResponse<ProductResponse>> update(@PathVariable String id, @Valid @RequestBody UpdateProductRequest request) {
        return ResponseEntity.ok(ApiResponse.success(productService.update(id, request), "Product updated"));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Soft-delete product (SELLER/ADMIN)")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable String id) {
        productService.delete(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Product deleted"));
    }

    @GetMapping("/seller/my-products")
    @Operation(summary = "Get products by authenticated seller")
    public ResponseEntity<ApiResponse<Page<ProductResponse>>> myProducts(@RequestHeader("X-Auth-User-Id") String sellerId, @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(productService.getBySeller(sellerId, pageable)));
    }

    // ── Internal stock endpoints (called by order-service via Feign) ─────────

    @GetMapping("/{productId}/stock")
    @Operation(summary = "Get current stock (not cached)")
    public ResponseEntity<ApiResponse<StockResponse>> getStock(@PathVariable String productId) {
        return ResponseEntity.ok(ApiResponse.success(productService.getStock(productId)));
    }

    @PostMapping("/{productId}/reserve-stock")
    @Operation(summary = "Reserve stock for a pending order")
    public ResponseEntity<ApiResponse<Void>> reserveStock(@PathVariable String productId, @Valid @RequestBody StockUpdateRequest request) {
        productService.reserveStock(productId, request);
        return ResponseEntity.ok(ApiResponse.success(null, "Stock reserved"));
    }

    @PostMapping("/{productId}/release-stock")
    @Operation(summary = "Release reserved stock (on order cancel/payment fail)")
    public ResponseEntity<ApiResponse<Void>> releaseStock(@PathVariable String productId, @Valid @RequestBody StockUpdateRequest request) {
        productService.releaseStock(productId, request);
        return ResponseEntity.ok(ApiResponse.success(null, "Stock released"));
    }

    @PostMapping("/{productId}/confirm-stock")
    @Operation(summary = "Permanently deduct stock (on payment success)")
    public ResponseEntity<ApiResponse<Void>> confirmStock(@PathVariable String productId, @Valid @RequestBody StockUpdateRequest request) {
        productService.confirmStockDeduction(productId, request);
        return ResponseEntity.ok(ApiResponse.success(null, "Stock deducted"));
    }
}
