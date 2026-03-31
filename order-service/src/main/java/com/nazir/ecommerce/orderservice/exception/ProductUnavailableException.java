package com.nazir.ecommerce.orderservice.exception;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class ProductUnavailableException extends RuntimeException {
    public ProductUnavailableException(String msg) { super(msg); }
}
