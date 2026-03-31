package com.nazir.ecommerce.orderservice.dto.response;

import com.nazir.ecommerce.orderservice.model.Order;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class OrderResponse {
    private UUID   id;
    private String orderNumber;
    private UUID   userId;
    private String userEmail;
    private Order.OrderStatus status;
    private List<ItemResponse> items;
    private AddressResponse    shippingAddress;
    private BigDecimal subtotal;
    private BigDecimal shippingCost;
    private BigDecimal taxAmount;
    private BigDecimal totalAmount;
    private String     currency;
    private String     paymentId;
    private String     cancellationReason;
    private String     notes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime confirmedAt;
    private LocalDateTime shippedAt;
    private LocalDateTime deliveredAt;

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ItemResponse {
        private UUID       id;
        private String     productId;
        private String     productName;
        private String     productSku;
        private Integer    quantity;
        private BigDecimal unitPrice;
        private BigDecimal totalPrice;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class AddressResponse {
        private String streetAddress;
        private String city;
        private String state;
        private String postalCode;
        private String country;
    }
}
