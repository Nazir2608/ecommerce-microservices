package com.nazir.ecommerce.orderservice.service.impl;

import com.nazir.ecommerce.orderservice.client.ProductServiceClient;
import com.nazir.ecommerce.orderservice.client.dto.ApiResponseWrapper;
import com.nazir.ecommerce.orderservice.client.dto.ProductInfo;
import com.nazir.ecommerce.orderservice.client.dto.StockRequest;
import com.nazir.ecommerce.orderservice.dto.request.PlaceOrderRequest;
import com.nazir.ecommerce.orderservice.dto.response.OrderResponse;
import com.nazir.ecommerce.orderservice.event.OrderEvent;
import com.nazir.ecommerce.orderservice.event.OrderEventPublisher;
import com.nazir.ecommerce.orderservice.event.PaymentEvent;
import com.nazir.ecommerce.orderservice.exception.OrderNotFoundException;
import com.nazir.ecommerce.orderservice.exception.ProductUnavailableException;
import com.nazir.ecommerce.orderservice.mapper.OrderMapper;
import com.nazir.ecommerce.orderservice.model.Order;
import com.nazir.ecommerce.orderservice.model.OrderItem;
import com.nazir.ecommerce.orderservice.repository.OrderRepository;
import com.nazir.ecommerce.orderservice.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Order service implementation — the heart of Phase 3.
 *
 * ═══════════════════════════════════════════════════════════════════════════
 * SAGA FLOW (Choreography pattern):
 * ═══════════════════════════════════════════════════════════════════════════
 *
 *  placeOrder():
 *    1. Fetch product details via Feign → circuit breaker guards the call
 *    2. Reserve stock for each item
 *    3. Save order in PENDING state (PostgreSQL)
 *    4. Publish ORDER_CREATED to Kafka → payment-service picks it up
 *
 *  handlePaymentEvent() [@KafkaListener on payment.events]:
 *    PAYMENT_SUCCESS:
 *      5. Confirm stock deduction (moves from reserved → confirmed)
 *      6. Transition order PENDING → CONFIRMED
 *      7. Publish ORDER_CONFIRMED → notification-service sends email
 *
 *    PAYMENT_FAILED:
 *      5. Release stock reservation (undo step 2)
 *      6. Cancel order with reason
 *      7. Publish ORDER_CANCELLED → notification-service sends email
 *
 * ═══════════════════════════════════════════════════════════════════════════
 * IDEMPOTENCY:
 * ═══════════════════════════════════════════════════════════════════════════
 *   Kafka at-least-once delivery means handlePaymentEvent() may be called
 *   twice for the same payment. Guard: check order status before processing.
 *   If order is already CONFIRMED/CANCELLED → skip (idempotent).
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class OrderServiceImpl implements OrderService {

    private final OrderRepository     orderRepository;
    private final ProductServiceClient productClient;
    private final OrderEventPublisher  eventPublisher;
    private final OrderMapper          orderMapper;

    // ── Place order (Saga start) ──────────────────────────────────────────

    @Override
    public OrderResponse placeOrder(PlaceOrderRequest request, UUID userId, String userEmail) {
        log.info("Placing order for userId={}, items={}", userId, request.getItems().size());

        List<OrderItem> items      = new ArrayList<>();
        BigDecimal      subtotal   = BigDecimal.ZERO;

        // ── Step 1: Validate each product via Feign + reserve stock ───────
        for (PlaceOrderRequest.OrderItemRequest itemReq : request.getItems()) {
            ProductInfo product = fetchProductOrThrow(itemReq.getProductId());

            if (!product.isInStock() || product.getAvailableStock() < itemReq.getQuantity()) {
                // Compensating transaction: release stock reserved in previous iterations
                rollbackReservedStock(items, "order-rollback");
                throw new ProductUnavailableException(
                    "Insufficient stock for '" + product.getName() +
                    "'. Available: " + product.getAvailableStock() +
                    ", Requested: " + itemReq.getQuantity());
            }

            BigDecimal itemTotal = product.getPrice()
                    .multiply(BigDecimal.valueOf(itemReq.getQuantity()));

            OrderItem orderItem = OrderItem.builder()
                    .productId(product.getId())
                    .productName(product.getName())   // SNAPSHOT — immutable history
                    .productSku(product.getSku())
                    .quantity(itemReq.getQuantity())
                    .unitPrice(product.getPrice())    // SNAPSHOT — immutable history
                    .totalPrice(itemTotal)
                    .build();

            items.add(orderItem);
            subtotal = subtotal.add(itemTotal);

            // Reserve stock for this item (held for pending order)
            productClient.reserveStock(product.getId(),
                    StockRequest.builder()
                            .quantity(itemReq.getQuantity())
                            .orderId("TBD") // will update after save
                            .build());

            log.debug("Reserved {} units of product={}", itemReq.getQuantity(), product.getId());
        }

        // ── Step 2: Calculate totals ──────────────────────────────────────
        BigDecimal shippingCost = calculateShipping(subtotal);
        BigDecimal taxAmount    = subtotal.multiply(new BigDecimal("0.08"))
                                         .setScale(2, RoundingMode.HALF_UP); // 8% tax
        BigDecimal total        = subtotal.add(shippingCost).add(taxAmount);

        // ── Step 3: Build and persist the Order ───────────────────────────
        Order order = Order.builder()
                .orderNumber(generateOrderNumber())
                .userId(userId)
                .userEmail(userEmail)
                .items(items)
                .shippingAddress(buildShippingAddress(request.getShippingAddress()))
                .subtotal(subtotal)
                .shippingCost(shippingCost)
                .taxAmount(taxAmount)
                .totalAmount(total)
                .currency(request.getCurrency() != null ? request.getCurrency() : "USD")
                .notes(request.getNotes())
                .status(Order.OrderStatus.PENDING)
                .build();

        Order saved = orderRepository.save(order);

        // Set orderId on each item after save (items now have their parent orderId)
        saved.getItems().forEach(item -> item.setOrderId(saved.getId()));
        orderRepository.save(saved);

        // ── Step 4: Publish ORDER_CREATED → triggers payment-service ──────
        eventPublisher.publish(saved, OrderEvent.EventType.ORDER_CREATED);

        log.info("Order placed: id={} number={} total={}", saved.getId(), saved.getOrderNumber(), total);
        return orderMapper.toResponse(saved);
    }

    // ── Payment event handler (Saga reaction) ─────────────────────────────

    /**
     * LEARNING POINT — @KafkaListener:
     *   groupId = "order-service-group" → one consumer group per service.
     *   All instances of order-service share the group → each message processed ONCE.
     *   If order-service has 3 instances → Kafka assigns partitions across all 3.
     *
     * LEARNING POINT — Idempotency guard:
     *   If this method is called twice for same payment (Kafka at-least-once):
     *   First call: order is PENDING → process normally
     *   Second call: order is already CONFIRMED/CANCELLED → skip
     */
    @Override
    @KafkaListener(
        topics   = "payment.events",
        groupId  = "order-service-group",
        containerFactory = "paymentEventListenerFactory"
    )
    public void handlePaymentEvent(PaymentEvent event) {
        if (event == null || event.getOrderId() == null) {
            log.warn("Received null or invalid payment event, skipping");
            return;
        }

        log.info("Received payment event: type={} orderId={}", event.getEventType(), event.getOrderId());

        Order order = orderRepository.findByIdWithItems(event.getOrderId())
                .orElseGet(() -> {
                    log.warn("Order {} not found for payment event — may have been deleted", event.getOrderId());
                    return null;
                });

        if (order == null) return;

        // ── Idempotency guard ──────────────────────────────────────────────
        if (order.getStatus() != Order.OrderStatus.PENDING) {
            log.info("Order {} already in status {} — skipping payment event (idempotent)", order.getId(), order.getStatus());
            return;
        }

        if (event.getEventType() == PaymentEvent.EventType.PAYMENT_SUCCESS) {
            handlePaymentSuccess(order, event);
        } else {
            handlePaymentFailure(order, event);
        }
    }

    private void handlePaymentSuccess(Order order, PaymentEvent event) {
        // Confirm stock deduction (permanent — payment is done)
        for (OrderItem item : order.getItems()) {
            try {
                productClient.confirmStock(item.getProductId(),
                        StockRequest.builder()
                                .quantity(item.getQuantity())
                                .orderId(order.getId().toString())
                                .build());
            } catch (Exception e) {
                // Log but don't fail — payment already processed, stock can be reconciled manually
                log.error("Failed to confirm stock for product={} orderId={}: {}", item.getProductId(), order.getId(), e.getMessage());
            }
        }

        // Transition order state
        order.confirm(event.getTransactionId());
        orderRepository.save(order);

        // Publish → notification-service sends confirmation email
        eventPublisher.publish(order, OrderEvent.EventType.ORDER_CONFIRMED);
        log.info("Order {} CONFIRMED. transactionId={}", order.getId(), event.getTransactionId());
    }

    private void handlePaymentFailure(Order order, PaymentEvent event) {
        // Release stock reservations (compensating transaction)
        rollbackReservedStock(order.getItems(), order.getId().toString());

        String reason = event.getFailureReason() != null
                ? "Payment failed: " + event.getFailureReason()
                : "Payment failed";

        order.cancel(reason);
        orderRepository.save(order);

        eventPublisher.publish(order, OrderEvent.EventType.ORDER_CANCELLED);
        log.info("Order {} CANCELLED due to payment failure: {}", order.getId(), reason);
    }

    // ── Read operations ───────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getById(UUID orderId, UUID userId) {
        Order order = orderRepository.findByIdAndUserIdWithItems(orderId, userId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found: " + orderId + " for user: " + userId));
        return orderMapper.toResponse(order);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getByIdAdmin(UUID orderId) {
        return orderMapper.toResponse(findOrderWithItems(orderId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderResponse> getMyOrders(UUID userId) {
        return orderRepository.findByUserIdWithItems(userId).stream().map(orderMapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OrderResponse> getAllOrders(Pageable pageable) {
        return orderRepository.findAll(pageable).map(orderMapper::toResponse);
    }

    // ── Write operations ──────────────────────────────────────────────────

    @Override
    public OrderResponse cancelOrder(UUID orderId, UUID userId, String reason) {
        Order order = orderRepository.findByIdAndUserIdWithItems(orderId, userId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found: " + orderId));

        order.cancel(reason != null ? reason : "Cancelled by customer");

        // Release stock reservations if order was PENDING
        if (order.getStatus() == Order.OrderStatus.CANCELLED) {
            rollbackReservedStock(order.getItems(), orderId.toString());
        }

        Order saved = orderRepository.save(order);
        eventPublisher.publish(saved, OrderEvent.EventType.ORDER_CANCELLED);
        log.info("Order {} cancelled by user={}", orderId, userId);
        return orderMapper.toResponse(saved);
    }

    @Override
    public OrderResponse shipOrder(UUID orderId) {
        Order order = findOrderWithItems(orderId);
        order.ship();
        Order saved = orderRepository.save(order);
        eventPublisher.publish(saved, OrderEvent.EventType.ORDER_SHIPPED);
        return orderMapper.toResponse(saved);
    }

    @Override
    public OrderResponse deliverOrder(UUID orderId) {
        Order order = findOrderWithItems(orderId);
        order.deliver();
        Order saved = orderRepository.save(order);
        eventPublisher.publish(saved, OrderEvent.EventType.ORDER_DELIVERED);
        return orderMapper.toResponse(saved);
    }

    // ── Private helpers ───────────────────────────────────────────────────

    private ProductInfo fetchProductOrThrow(String productId) {
        try {
            ApiResponseWrapper<ProductInfo> response = productClient.getProduct(productId);
            if (response == null || !response.isSuccess() || response.getData() == null) {
                throw new ProductUnavailableException("Product not found or unavailable: " + productId);
            }
            return response.getData();
        } catch (ProductUnavailableException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to fetch product {}: {}", productId, e.getMessage());
            throw new ProductUnavailableException(
                "Product service error for product: " + productId + ". " + e.getMessage());
        }
    }

    private void rollbackReservedStock(List<OrderItem> items, String orderId) {
        for (OrderItem item : items) {
            try {
                productClient.releaseStock(item.getProductId(),
                        StockRequest.builder()
                                .quantity(item.getQuantity())
                                .orderId(orderId)
                                .build());
                log.debug("Released stock for product={} qty={}", item.getProductId(), item.getQuantity());
            } catch (Exception e) {
                log.error("Failed to release stock for product={}: {}", item.getProductId(), e.getMessage());
            }
        }
    }

    private Order findOrderWithItems(UUID orderId) {
        return orderRepository.findByIdWithItems(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found: " + orderId));
    }

    private BigDecimal calculateShipping(BigDecimal subtotal) {
        // Free shipping over $50, otherwise $5.99 flat rate
        return subtotal.compareTo(new BigDecimal("50.00")) >= 0
                ? BigDecimal.ZERO
                : new BigDecimal("5.99");
    }

    private Order.ShippingAddress buildShippingAddress(PlaceOrderRequest.ShippingAddressRequest req) {
        return Order.ShippingAddress.builder()
                .streetAddress(req.getStreetAddress())
                .city(req.getCity())
                .state(req.getState())
                .postalCode(req.getPostalCode())
                .country(req.getCountry())
                .build();
    }

    private String generateOrderNumber() {
        String datePart = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String randomPart = String.format("%04X", new Random().nextInt(0xFFFF));
        String number = "ORD-" + datePart + "-" + randomPart;
        // Retry if collision (extremely rare)
        while (orderRepository.existsByOrderNumber(number)) {
            number = "ORD-" + datePart + "-" + String.format("%04X", new Random().nextInt(0xFFFF));
        }
        return number;
    }
}
