package com.nazir.ecommerce.userservice.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Generic API response envelope — wraps all responses in a consistent shape.
 *
 * Success:  { success: true,  data: {...},  message: "User registered" }
 * Error:    { success: false, data: null,   message: "Email already exists" }
 *
 * LEARNING POINT — Why use an envelope:
 *   Clients (mobile / frontend) know exactly where the data is.
 *   They can check `success` first before processing `data`.
 *   Easier to add metadata (pagination, request IDs) without breaking clients.
 *
 * @param <T> the type of the data payload
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)  // omit null fields from JSON output
public class ApiResponse<T> {

    private boolean success;
    private String message;
    private T data;

    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();

    // ─── Factory methods ──────────────────────────────────────────────────────

    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .data(data)
                .build();
    }

    public static <T> ApiResponse<T> success(T data, String message) {
        return ApiResponse.<T>builder()
                .success(true)
                .data(data)
                .message(message)
                .build();
    }

    public static <T> ApiResponse<T> error(String message) {
        return ApiResponse.<T>builder()
                .success(false)
                .message(message)
                .build();
    }
}
