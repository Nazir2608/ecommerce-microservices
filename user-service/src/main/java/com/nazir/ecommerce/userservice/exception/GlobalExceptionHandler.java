package com.nazir.ecommerce.userservice.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Centralised exception handler — all exceptions funnel through here.
 *
 * ┌──────────────────────────────────────────────────────────────────────────┐
 * │  LEARNING POINT — RFC 7807 ProblemDetail (Spring 6+)                     │
 * │                                                                          │
 * │  ProblemDetail is a standard JSON error response format:                 │
 * │  {                                                                       │
 * │    "type":     "https://api.ecommerce.com/errors/validation",            │
 * │    "title":    "Validation Failed",                                      │
 * │    "status":   400,                                                      │
 * │    "detail":   "2 field(s) failed validation",                           │
 * │    "timestamp": "2024-01-01T10:00:00Z",                                  │
 * │    "errors":   { "email": "must not be blank", "password": "..." }       │
 * │  }                                                                       │
 * │                                                                          │
 * │  Clients know exactly where to find the error and any extra fields.     │
 * │  Spring 6 includes ProblemDetail as a first-class return type.          │
 * └──────────────────────────────────────────────────────────────────────────┘
 *
 * ┌──────────────────────────────────────────────────────────────────────────┐
 * │  LEARNING POINT — @RestControllerAdvice                                  │
 * │                                                                          │
 * │  = @ControllerAdvice + @ResponseBody                                     │
 * │  Catches exceptions thrown from any @RestController in the application. │
 * │  The matched @ExceptionHandler method determines the response.           │
 * └──────────────────────────────────────────────────────────────────────────┘
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    private static final String BASE_URI = "https://api.ecommerce.com/errors";

    // ─── Validation ───────────────────────────────────────────────────────────

    /**
     * Handles @Valid / @Validated failures — returns each field's error message.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(fe.getField(), fe.getDefaultMessage());
        }

        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setType(URI.create(BASE_URI + "/validation"));
        problem.setTitle("Validation Failed");
        problem.setDetail(fieldErrors.size() + " field(s) failed validation");
        problem.setProperty("errors",    fieldErrors);
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    // ─── Domain exceptions ────────────────────────────────────────────────────

    @ExceptionHandler(UserAlreadyExistsException.class)
    public ProblemDetail handleUserAlreadyExists(UserAlreadyExistsException ex) {
        return buildProblem(HttpStatus.CONFLICT, "user-exists", "User Already Exists", ex.getMessage());
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ProblemDetail handleUserNotFound(UserNotFoundException ex) {
        return buildProblem(HttpStatus.NOT_FOUND, "user-not-found", "User Not Found", ex.getMessage());
    }

    @ExceptionHandler(InvalidTokenException.class)
    public ProblemDetail handleInvalidToken(InvalidTokenException ex) {
        return buildProblem(HttpStatus.UNAUTHORIZED, "invalid-token", "Invalid Token", ex.getMessage());
    }

    @ExceptionHandler(AccountLockedException.class)
    public ProblemDetail handleAccountLocked(AccountLockedException ex) {
        return buildProblem(HttpStatus.FORBIDDEN, "account-locked", "Account Locked", ex.getMessage());
    }

    @ExceptionHandler(PasswordMismatchException.class)
    public ProblemDetail handlePasswordMismatch(PasswordMismatchException ex) {
        return buildProblem(HttpStatus.BAD_REQUEST, "password-mismatch", "Password Mismatch", ex.getMessage());
    }

    // ─── Spring Security exceptions ───────────────────────────────────────────

    @ExceptionHandler(BadCredentialsException.class)
    public ProblemDetail handleBadCredentials(BadCredentialsException ex) {
        // Return a generic message — don't tell attackers whether email or password is wrong
        return buildProblem(HttpStatus.UNAUTHORIZED, "auth-failed",
                "Authentication Failed", "Invalid email or password");
    }

    @ExceptionHandler(DisabledException.class)
    public ProblemDetail handleDisabled(DisabledException ex) {
        return buildProblem(HttpStatus.FORBIDDEN, "account-disabled",
                "Account Disabled", "Your account is disabled. Please contact support.");
    }

    @ExceptionHandler(LockedException.class)
    public ProblemDetail handleLocked(LockedException ex) {
        return buildProblem(HttpStatus.FORBIDDEN, "account-locked",
                "Account Locked", "Your account is temporarily locked due to too many failed login attempts.");
    }

    // ─── Catch-all ────────────────────────────────────────────────────────────

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGeneric(Exception ex) {
        log.error("Unhandled exception: {}", ex.getMessage(), ex);
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        problem.setType(URI.create(BASE_URI + "/internal-error"));
        problem.setTitle("Internal Server Error");
        problem.setDetail("An unexpected error occurred. Please try again later.");
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    // ─── Helper ───────────────────────────────────────────────────────────────

    private ProblemDetail buildProblem(HttpStatus status, String errorCode,
                                        String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatus(status);
        problem.setType(URI.create(BASE_URI + "/" + errorCode));
        problem.setTitle(title);
        problem.setDetail(detail);
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }
}
