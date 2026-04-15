package com.multitenant.app.common.exception;

import com.multitenant.app.common.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

/**
 * Global exception handler that catches all exceptions thrown by controllers/services
 * and converts them into consistent ApiResponse JSON with appropriate HTTP status codes.
 *
 * WHY: Without this, Spring Boot returns its default error format (Whitelabel error page
 * or inconsistent JSON). This ensures every error response has the same shape as success
 * responses, so frontend code only needs one response parser.
 *
 * @RestControllerAdvice = @ControllerAdvice + @ResponseBody
 * It intercepts exceptions from ALL controllers in the application.
 *
 * EXCEPTION -> HTTP STATUS MAPPING:
 *   TenantNotFoundException      -> 404 (tenant slug doesn't exist)
 *   TenantResolutionException    -> 400 (missing/invalid tenant header)
 *   UnauthorizedAccessException  -> 403 (custom auth check failed)
 *   ResourceNotFoundException    -> 404 (entity not found by ID)
 *   BusinessException            -> 422 (business rule violated)
 *   AccessDeniedException        -> 403 (Spring Security @PreAuthorize failed)
 *   MethodArgumentNotValid       -> 400 (Jakarta validation @NotBlank, @Email, etc.)
 *   Exception (catch-all)        -> 500 (unexpected error, logged with stack trace)
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(TenantNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleTenantNotFound(TenantNotFoundException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(TenantResolutionException.class)
    public ResponseEntity<ApiResponse<Void>> handleTenantResolution(TenantResolutionException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(UnauthorizedAccessException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnauthorized(UnauthorizedAccessException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleResourceNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ApiResponse.error(ex.getMessage(), request.getRequestURI()));
    }

    /**
     * Catches Spring Security's AccessDeniedException thrown when @PreAuthorize fails.
     * Example: A user with ROLE1 tries to hit an endpoint annotated @PreAuthorize(HAS_ADMIN).
     * We intentionally don't expose the original message to avoid leaking role info.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("Access denied", request.getRequestURI()));
    }

    /**
     * Catches validation errors from @Valid on request bodies.
     * Collects all field-level errors into a map: {"email": "Invalid email format", "password": "..."}
     * so the frontend can display errors next to each form field.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        ApiResponse<Map<String, String>> response = ApiResponse.<Map<String, String>>builder()
                .success(false)
                .message("Validation failed")
                .data(errors)
                .path(request.getRequestURI())
                .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /**
     * Catch-all for any unhandled exception. This is the safety net.
     * Logs the full stack trace at ERROR level for debugging, but returns a
     * generic message to the client to avoid leaking internal details.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneral(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception at {}: {}", request.getRequestURI(), ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("An unexpected error occurred", request.getRequestURI()));
    }
}
