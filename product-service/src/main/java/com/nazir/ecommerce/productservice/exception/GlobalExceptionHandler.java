package com.nazir.ecommerce.productservice.exception;

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

    private static final String BASE_URI = "https://api.ecommerce.com/errors";

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            errors.put(fe.getField(), fe.getDefaultMessage());
        }
        ProblemDetail p = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        p.setType(URI.create(BASE_URI + "/validation"));
        p.setTitle("Validation Failed");
        p.setDetail(errors.size() + " field(s) failed validation");
        p.setProperty("errors", errors);
        p.setProperty("timestamp", Instant.now());
        return p;
    }

    @ExceptionHandler(ProductNotFoundException.class)
    public ProblemDetail handleNotFound(ProductNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, "product-not-found", "Product Not Found", ex.getMessage());
    }

    @ExceptionHandler(InsufficientStockException.class)
    public ProblemDetail handleInsufficientStock(InsufficientStockException ex) {
        return build(HttpStatus.CONFLICT, "insufficient-stock", "Insufficient Stock", ex.getMessage());
    }

    @ExceptionHandler(DuplicateSkuException.class)
    public ProblemDetail handleDuplicateSku(DuplicateSkuException ex) {
        return build(HttpStatus.CONFLICT, "duplicate-sku", "Duplicate SKU", ex.getMessage());
    }

    @ExceptionHandler(org.springframework.dao.OptimisticLockingFailureException.class)
    public ProblemDetail handleOptimisticLock(Exception ex) {
        return build(HttpStatus.CONFLICT, "concurrent-update",
                "Concurrent Update", "Resource was modified by another request. Please retry.");
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGeneric(Exception ex) {
        log.error("Unhandled exception: {}", ex.getMessage(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "internal-error",
                "Internal Server Error", "An unexpected error occurred. Please try again.");
    }

    private ProblemDetail build(HttpStatus status, String code, String title, String detail) {
        ProblemDetail p = ProblemDetail.forStatus(status);
        p.setType(URI.create(BASE_URI + "/" + code));
        p.setTitle(title);
        p.setDetail(detail);
        p.setProperty("timestamp", Instant.now());
        return p;
    }
}
