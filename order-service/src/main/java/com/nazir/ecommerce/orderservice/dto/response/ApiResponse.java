package com.nazir.ecommerce.orderservice.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import java.time.LocalDateTime;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {
    private boolean success;
    private String  message;
    private T       data;
    @Builder.Default private LocalDateTime timestamp = LocalDateTime.now();

    public static <T> ApiResponse<T> success(T data)              { return ApiResponse.<T>builder().success(true).data(data).build(); }
    public static <T> ApiResponse<T> success(T data, String msg)  { return ApiResponse.<T>builder().success(true).data(data).message(msg).build(); }
    public static <T> ApiResponse<T> error(String msg)            { return ApiResponse.<T>builder().success(false).message(msg).build(); }
}
