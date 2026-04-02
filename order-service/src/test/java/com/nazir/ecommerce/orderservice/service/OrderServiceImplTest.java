package com.nazir.ecommerce.orderservice.service;

import com.nazir.ecommerce.orderservice.client.ProductServiceClient;
import com.nazir.ecommerce.orderservice.client.dto.ApiResponseWrapper;
import com.nazir.ecommerce.orderservice.client.dto.ProductInfo;
import com.nazir.ecommerce.orderservice.dto.request.PlaceOrderRequest;
import com.nazir.ecommerce.orderservice.dto.response.OrderResponse;
import com.nazir.ecommerce.orderservice.event.OrderEventPublisher;
import com.nazir.ecommerce.orderservice.event.PaymentEvent;
import com.nazir.ecommerce.orderservice.exception.OrderNotFoundException;
import com.nazir.ecommerce.orderservice.exception.ProductUnavailableException;
import com.nazir.ecommerce.orderservice.mapper.OrderMapper;
import com.nazir.ecommerce.orderservice.model.Order;
import com.nazir.ecommerce.orderservice.model.OrderItem;
import com.nazir.ecommerce.orderservice.repository.OrderRepository;
import com.nazir.ecommerce.orderservice.service.impl.OrderServiceImpl;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderServiceImpl")
class OrderServiceImplTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private ProductServiceClient productClient;
    @Mock
    private OrderEventPublisher eventPublisher;
    @Mock
    private OrderMapper orderMapper;

    @InjectMocks
    private OrderServiceImpl orderService;

    private static final UUID USER_ID = UUID.randomUUID();

    // ── Helpers ───────────────────────────────────────────────────────────

    private PlaceOrderRequest buildRequest(String productId, int qty) {
        return PlaceOrderRequest.builder()
                .items(List.of(PlaceOrderRequest.OrderItemRequest.builder()
                        .productId(productId).quantity(qty).build()))
                .shippingAddress(PlaceOrderRequest.ShippingAddressRequest.builder()
                        .streetAddress("123 Main St").city("Mumbai")
                        .state("MH").postalCode("400001").country("IN").build())
                .build();
    }

    private ApiResponseWrapper<ProductInfo> successWrapper(String productId, int stock) {
        ProductInfo info = ProductInfo.builder()
                .id(productId).name("Test Product").sku("TST-001")
                .price(new BigDecimal("99.99")).availableStock(stock).inStock(stock > 0)
                .status("ACTIVE").build();
        ApiResponseWrapper<ProductInfo> w = new ApiResponseWrapper<>();
        w.setSuccess(true);
        w.setData(info);
        return w;
    }

    private Order pendingOrder() {
        OrderItem item = OrderItem.builder()
                .id(UUID.randomUUID()).productId("prod-1")
                .productName("Test Product").quantity(2)
                .unitPrice(new BigDecimal("99.99")).totalPrice(new BigDecimal("199.98"))
                .build();
        return Order.builder()
                .id(UUID.randomUUID()).orderNumber("ORD-20240315-A1B2")
                .userId(USER_ID).userEmail("user@example.com")
                .status(Order.OrderStatus.PENDING)
                .subtotal(new BigDecimal("199.98"))
                .shippingCost(BigDecimal.ZERO)
                .taxAmount(new BigDecimal("16.00"))
                .totalAmount(new BigDecimal("215.98"))
                .currency("USD").items(new ArrayList<>(List.of(item)))
                .build();
    }

    // ── placeOrder tests ──────────────────────────────────────────────────

    @Nested
    @DisplayName("placeOrder()")
    class PlaceOrderTests {

        @Test
        @DisplayName("should create PENDING order when product is in stock")
        void placeOrder_success() {
            given(productClient.getProduct("prod-1")).willReturn(successWrapper("prod-1", 10));
            given(orderRepository.existsByOrderNumber(anyString())).willReturn(false);
            given(orderRepository.save(any())).willAnswer(inv -> {
                Order o = inv.getArgument(0);
                o = Order.builder().id(UUID.randomUUID()).orderNumber("ORD-TEST")
                        .userId(o.getUserId()).userEmail(o.getUserEmail())
                        .status(o.getStatus()).subtotal(o.getSubtotal())
                        .shippingCost(o.getShippingCost()).taxAmount(o.getTaxAmount())
                        .totalAmount(o.getTotalAmount()).currency(o.getCurrency())
                        .items(o.getItems() != null ? o.getItems() : new ArrayList<>()).build();
                return o;
            });
            given(orderMapper.toResponse(any())).willReturn(OrderResponse.builder().build());

            OrderResponse result = orderService.placeOrder(
                    buildRequest("prod-1", 2), USER_ID, "user@example.com");

            assertThat(result).isNotNull();
            then(productClient).should().reserveStock(eq("prod-1"), any());
            then(eventPublisher).should().publish(any(), eq(com.nazir.ecommerce.orderservice.event.OrderEvent.EventType.ORDER_CREATED));
        }

        @Test
        @DisplayName("should throw ProductUnavailableException when product has insufficient stock")
        void placeOrder_insufficientStock() {
            given(productClient.getProduct("prod-1")).willReturn(successWrapper("prod-1", 1));

            assertThatThrownBy(() ->
                    orderService.placeOrder(buildRequest("prod-1", 5), USER_ID, "user@example.com"))
                    .isInstanceOf(ProductUnavailableException.class)
                    .hasMessageContaining("Insufficient stock");

            then(orderRepository).should(never()).save(any());
            then(eventPublisher).should(never()).publish(any(), any());
        }

        @Test
        @DisplayName("should throw ProductUnavailableException when product-service returns null data")
        void placeOrder_productNotFound() {
            ApiResponseWrapper<ProductInfo> empty = new ApiResponseWrapper<>();
            empty.setSuccess(false);
            given(productClient.getProduct("missing")).willReturn(empty);

            assertThatThrownBy(() ->
                    orderService.placeOrder(buildRequest("missing", 1), USER_ID, "user@example.com"))
                    .isInstanceOf(ProductUnavailableException.class);
        }
    }

    // ── handlePaymentEvent tests ──────────────────────────────────────────

    @Nested
    @DisplayName("handlePaymentEvent()")
    class PaymentEventTests {

        @Test
        @DisplayName("should confirm order and deduct stock on PAYMENT_SUCCESS")
        void paymentSuccess_confirmsOrder() {
            Order order = pendingOrder();
            PaymentEvent event = PaymentEvent.builder()
                    .eventType(PaymentEvent.EventType.PAYMENT_SUCCESS)
                    .orderId(order.getId()).transactionId("txn-001")
                    .amount(order.getTotalAmount()).build();

            given(orderRepository.findByIdWithItems(order.getId())).willReturn(Optional.of(order));
            given(orderRepository.save(any())).willReturn(order);
            given(orderMapper.toResponse(any())).willReturn(OrderResponse.builder().build());

            orderService.handlePaymentEvent(event);

            assertThat(order.getStatus()).isEqualTo(Order.OrderStatus.CONFIRMED);
            assertThat(order.getPaymentId()).isEqualTo("txn-001");
            then(productClient).should().confirmStock(eq("prod-1"), any());
            then(eventPublisher).should().publish(any(), eq(com.nazir.ecommerce.orderservice.event.OrderEvent.EventType.ORDER_CONFIRMED));
        }

        @Test
        @DisplayName("should cancel order and release stock on PAYMENT_FAILED")
        void paymentFailed_cancelsOrder() {
            Order order = pendingOrder();
            PaymentEvent event = PaymentEvent.builder()
                    .eventType(PaymentEvent.EventType.PAYMENT_FAILED)
                    .orderId(order.getId()).failureReason("Insufficient funds").build();

            given(orderRepository.findByIdWithItems(order.getId())).willReturn(Optional.of(order));
            given(orderRepository.save(any())).willReturn(order);

            orderService.handlePaymentEvent(event);

            assertThat(order.getStatus()).isEqualTo(Order.OrderStatus.CANCELLED);
            assertThat(order.getCancellationReason()).contains("Insufficient funds");
            then(productClient).should().releaseStock(eq("prod-1"), any());
            then(eventPublisher).should().publish(any(), eq(com.nazir.ecommerce.orderservice.event.OrderEvent.EventType.ORDER_CANCELLED));
        }

        @Test
        @DisplayName("should be idempotent — skip if order already CONFIRMED")
        void paymentSuccess_idempotent_skipIfAlreadyConfirmed() {
            Order order = pendingOrder();
            order.confirm("txn-already");  // already confirmed

            PaymentEvent event = PaymentEvent.builder()
                    .eventType(PaymentEvent.EventType.PAYMENT_SUCCESS)
                    .orderId(order.getId()).transactionId("txn-duplicate").build();

            given(orderRepository.findByIdWithItems(order.getId())).willReturn(Optional.of(order));

            orderService.handlePaymentEvent(event);

            // No second confirm, no stock deduction, no event published
            then(productClient).should(never()).confirmStock(any(), any());
            then(eventPublisher).should(never()).publish(any(), any());
        }
    }

    // ── getById tests ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("getById()")
    class GetByIdTests {

        @Test
        @DisplayName("should throw OrderNotFoundException when order not found for user")
        void getById_notFound() {
            UUID orderId = UUID.randomUUID();
            given(orderRepository.findByIdAndUserIdWithItems(orderId, USER_ID))
                    .willReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.getById(orderId, USER_ID))
                    .isInstanceOf(OrderNotFoundException.class);
        }
    }

    // ── State machine tests ───────────────────────────────────────────────

    @Nested
    @DisplayName("Order state machine")
    class StateMachineTests {

        @Test
        @DisplayName("confirm() transitions PENDING → CONFIRMED")
        void confirm_fromPending() {
            Order order = pendingOrder();
            order.confirm("txn-123");
            assertThat(order.getStatus()).isEqualTo(Order.OrderStatus.CONFIRMED);
            assertThat(order.getPaymentId()).isEqualTo("txn-123");
            assertThat(order.getConfirmedAt()).isNotNull();
        }

        @Test
        @DisplayName("confirm() throws when order is not PENDING")
        void confirm_wrongState() {
            Order order = pendingOrder();
            order.confirm("txn-1");  // now CONFIRMED
            assertThatThrownBy(() -> order.confirm("txn-2"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("CONFIRMED");
        }

        @Test
        @DisplayName("cancel() throws when order is SHIPPED")
        void cancel_shippedOrder() {
            Order order = pendingOrder();
            order.confirm("txn-1");
            order.ship();
            assertThatThrownBy(() -> order.cancel("Customer changed mind"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("SHIPPED");
        }
    }
}
