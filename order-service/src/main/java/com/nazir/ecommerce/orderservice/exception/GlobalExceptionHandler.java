package com.nazir.ecommerce.orderservice.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    private static final String BASE = "https://api.ecommerce.com/errors";

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors())
            errors.put(fe.getField(), fe.getDefaultMessage());
        ProblemDetail p = build(400, "validation", "Validation Failed", errors.size() + " field(s) failed");
        p.setProperty("errors", errors);
        return p;
    }

    @ExceptionHandler(OrderNotFoundException.class)
    public ProblemDetail handle(OrderNotFoundException ex) {
        return build(404, "order-not-found", "Order Not Found", ex.getMessage());
    }

    @ExceptionHandler(InvalidOrderStateException.class)
    public ProblemDetail handle(InvalidOrderStateException ex) {
        return build(409, "invalid-state", "Invalid Order State", ex.getMessage());
    }

    @ExceptionHandler(ProductUnavailableException.class)
    public ProblemDetail handle(ProductUnavailableException ex) {
        return build(400, "product-unavailable", "Product Unavailable", ex.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ProblemDetail handle(IllegalStateException ex) {
        return build(409, "invalid-state", "Invalid State", ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGeneric(Exception ex) {
        log.error("Unhandled: {}", ex.getMessage(), ex);
        return build(500, "internal-error", "Internal Server Error", "An unexpected error occurred.");
    }

    private ProblemDetail build(int status, String code, String title, String detail) {
        ProblemDetail p = ProblemDetail.forStatus(status);
        p.setType(URI.create(BASE + "/" + code));
        p.setTitle(title);
        p.setDetail(detail);
        p.setProperty("timestamp", Instant.now());
        return p;
    }
}
