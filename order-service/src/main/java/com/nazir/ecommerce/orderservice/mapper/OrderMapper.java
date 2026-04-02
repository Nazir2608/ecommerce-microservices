package com.nazir.ecommerce.orderservice.mapper;

import com.nazir.ecommerce.orderservice.dto.response.OrderResponse;
import com.nazir.ecommerce.orderservice.model.Order;
import com.nazir.ecommerce.orderservice.model.OrderItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface OrderMapper {

    @Mapping(target = "items", source = "items")
    @Mapping(target = "shippingAddress", source = "shippingAddress")
    OrderResponse toResponse(Order order);

    OrderResponse.ItemResponse toItemResponse(OrderItem item);

    OrderResponse.AddressResponse toAddressResponse(Order.ShippingAddress address);
}
