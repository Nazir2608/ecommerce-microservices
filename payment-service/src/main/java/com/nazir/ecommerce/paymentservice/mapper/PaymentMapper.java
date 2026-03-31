package com.nazir.ecommerce.paymentservice.mapper;

import com.nazir.ecommerce.paymentservice.dto.response.PaymentResponse;
import com.nazir.ecommerce.paymentservice.model.Payment;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface PaymentMapper {
    PaymentResponse toResponse(Payment payment);
}
