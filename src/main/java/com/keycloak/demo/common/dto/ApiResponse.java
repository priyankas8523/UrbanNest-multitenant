package com.keycloak.demo.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Unified API response wrapper used by ALL endpoints in the application.
 *
 * WHY: Every API response follows the same shape, making it predictable for frontend
 * consumers. They can always check response.success to know if the call worked,
 * and read response.data for the payload or response.message for errors.
 *
 * Example success response:
 * {
 *   "success": true,
 *   "message": "Company registered successfully",
 *   "data": { "tenantId": "acme-corp", ... },
 *   "timestamp": "2024-01-15T10:30:00Z"
 * }
 *
 * Example error response:
 * {
 *   "success": false,
 *   "message": "Email already registered as a tenant owner",
 *   "timestamp": "2024-01-15T10:30:00Z",
 *   "path": "/api/v1/auth/company/register"
 * }
 *
 * @JsonInclude(NON_NULL) - Null fields are omitted from JSON output.
 *                          So success responses won't have "path" and errors won't have "data".
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private boolean success;       // true = OK, false = something went wrong
    private String message;        // Human-readable status message
    private T data;                // The actual payload (null on error responses)
    @Builder.Default
    private String timestamp = Instant.now().toString();  // ISO-8601 timestamp for debugging
    private String path;           // The request URI (only populated on errors for debugging)

    /** Factory method for successful responses with data */
    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .message("Operation successful")
                .data(data)
                .build();
    }

    /** Factory method for successful responses with data and a custom message */
    public static <T> ApiResponse<T> success(T data, String message) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .data(data)
                .build();
    }

    /** Factory method for error responses (used by GlobalExceptionHandler) */
    public static <T> ApiResponse<T> error(String message) {
        return ApiResponse.<T>builder()
                .success(false)
                .message(message)
                .build();
    }

    /** Factory method for error responses that include the request path for debugging */
    public static <T> ApiResponse<T> error(String message, String path) {
        return ApiResponse.<T>builder()
                .success(false)
                .message(message)
                .path(path)
                .build();
    }
}
