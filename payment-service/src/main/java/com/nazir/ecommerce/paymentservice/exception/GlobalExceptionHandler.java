package com.nazir.ecommerce.paymentservice.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
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
        ex.getBindingResult().getFieldErrors().forEach(f -> errors.put(f.getField(), f.getDefaultMessage()));
        ProblemDetail p = build(400, "validation", "Validation Failed", errors.size() + " error(s)");
        p.setProperty("errors", errors);
        return p;
    }

    @ExceptionHandler(PaymentNotFoundException.class)
    public ProblemDetail handle(PaymentNotFoundException ex) {
        return build(404, "payment-not-found", "Payment Not Found", ex.getMessage());
    }

    @ExceptionHandler(DuplicatePaymentException.class)
    public ProblemDetail handle(DuplicatePaymentException ex) {
        return build(409, "duplicate-payment", "Duplicate Payment", ex.getMessage());
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
