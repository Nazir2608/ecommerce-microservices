package com.nazir.ecommerce.productservice.service;

import com.nazir.ecommerce.productservice.dto.request.CreateProductRequest;
import com.nazir.ecommerce.productservice.dto.request.StockUpdateRequest;
import com.nazir.ecommerce.productservice.dto.response.ProductResponse;
import com.nazir.ecommerce.productservice.event.StockEventPublisher;
import com.nazir.ecommerce.productservice.exception.InsufficientStockException;
import com.nazir.ecommerce.productservice.exception.ProductNotFoundException;
import com.nazir.ecommerce.productservice.mapper.ProductMapper;
import com.nazir.ecommerce.productservice.model.Product;
import com.nazir.ecommerce.productservice.repository.ProductRepository;
import com.nazir.ecommerce.productservice.service.impl.ProductServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProductServiceImpl")
class ProductServiceImplTest {

    @Mock private ProductRepository    productRepository;
    @Mock private ProductMapper        productMapper;
    @Mock private StockEventPublisher  eventPublisher;

    @InjectMocks
    private ProductServiceImpl productService;

    private Product sampleProduct;

    @BeforeEach
    void setUp() {
        sampleProduct = Product.builder()
                .id("prod-123")
                .sku("LAP-MAC-20240101")
                .name("MacBook Pro")
                .category("laptops")
                .price(new BigDecimal("1999.00"))
                .stockQuantity(50)
                .reservedQuantity(0)
                .status(Product.ProductStatus.ACTIVE)
                .sellerId("seller-1")
                .build();
    }

    @Nested
    @DisplayName("getById()")
    class GetByIdTests {

        @Test
        @DisplayName("should return product response when found")
        void getById_found() {
            ProductResponse expected = ProductResponse.builder().id("prod-123").name("MacBook Pro").build();
            given(productRepository.findById("prod-123")).willReturn(Optional.of(sampleProduct));
            given(productMapper.toResponse(sampleProduct)).willReturn(expected);

            ProductResponse result = productService.getById("prod-123");

            assertThat(result.getId()).isEqualTo("prod-123");
            then(productRepository).should().findById("prod-123");
        }

        @Test
        @DisplayName("should throw ProductNotFoundException when not found")
        void getById_notFound() {
            given(productRepository.findById("missing")).willReturn(Optional.empty());

            assertThatThrownBy(() -> productService.getById("missing"))
                    .isInstanceOf(ProductNotFoundException.class)
                    .hasMessageContaining("missing");
        }
    }

    @Nested
    @DisplayName("reserveStock()")
    class ReserveStockTests {

        @Test
        @DisplayName("should increment reservedQuantity when sufficient stock available")
        void reserveStock_success() {
            // 50 stock, 0 reserved → 50 available
            given(productRepository.findById("prod-123")).willReturn(Optional.of(sampleProduct));
            given(productRepository.save(any())).willReturn(sampleProduct);

            productService.reserveStock("prod-123", new StockUpdateRequest(10, "order-1"));

            assertThat(sampleProduct.getReservedQuantity()).isEqualTo(10);
            then(productRepository).should().save(sampleProduct);
            then(eventPublisher).should().publish(any());
        }

        @Test
        @DisplayName("should throw InsufficientStockException when not enough stock")
        void reserveStock_insufficientStock() {
            sampleProduct.setStockQuantity(5);
            sampleProduct.setReservedQuantity(3); // available = 5 - 3 = 2
            given(productRepository.findById("prod-123")).willReturn(Optional.of(sampleProduct));

            assertThatThrownBy(() ->
                    productService.reserveStock("prod-123", new StockUpdateRequest(10, "order-1")))
                    .isInstanceOf(InsufficientStockException.class)
                    .hasMessageContaining("Insufficient stock");

            then(productRepository).should(never()).save(any());
        }
    }

    @Nested
    @DisplayName("confirmStockDeduction()")
    class ConfirmStockTests {

        @Test
        @DisplayName("should decrement both stockQuantity and reservedQuantity")
        void confirmStock_success() {
            sampleProduct.setStockQuantity(50);
            sampleProduct.setReservedQuantity(10);
            given(productRepository.findById("prod-123")).willReturn(Optional.of(sampleProduct));
            given(productRepository.save(any())).willReturn(sampleProduct);

            productService.confirmStockDeduction("prod-123", new StockUpdateRequest(5, "order-1"));

            assertThat(sampleProduct.getStockQuantity()).isEqualTo(45);
            assertThat(sampleProduct.getReservedQuantity()).isEqualTo(5);
        }

        @Test
        @DisplayName("should set status to OUT_OF_STOCK when stockQuantity reaches 0")
        void confirmStock_outOfStock() {
            sampleProduct.setStockQuantity(5);
            sampleProduct.setReservedQuantity(5);
            given(productRepository.findById("prod-123")).willReturn(Optional.of(sampleProduct));
            given(productRepository.save(any())).willReturn(sampleProduct);

            productService.confirmStockDeduction("prod-123", new StockUpdateRequest(5, "order-1"));

            assertThat(sampleProduct.getStatus()).isEqualTo(Product.ProductStatus.OUT_OF_STOCK);
        }
    }

    @Nested
    @DisplayName("create()")
    class CreateTests {

        @Test
        @DisplayName("should create product with ACTIVE status and zero reservedQuantity")
        void create_success() {
            CreateProductRequest req = CreateProductRequest.builder()
                    .name("Sony Headphones").category("electronics")
                    .price(new BigDecimal("349.99")).stockQuantity(100).build();

            given(productRepository.existsBySku(anyString())).willReturn(false);
            given(productMapper.toEntity(req)).willReturn(sampleProduct);
            given(productRepository.save(any())).willReturn(sampleProduct);
            given(productMapper.toResponse(sampleProduct))
                    .willReturn(ProductResponse.builder().id("prod-123").build());

            ProductResponse result = productService.create(req, "seller-1");

            assertThat(result).isNotNull();
            then(productRepository).should().save(argThat(p ->
                    p.getStatus() == Product.ProductStatus.ACTIVE &&
                    p.getReservedQuantity() == 0
            ));
        }
    }
}
