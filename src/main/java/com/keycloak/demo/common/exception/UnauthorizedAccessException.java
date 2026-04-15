package com.keycloak.demo.common.exception;

/**
 * Thrown when a user tries to access a resource they don't have permission for.
 *
 * Triggers: 403 FORBIDDEN response via GlobalExceptionHandler.
 *
 * This is different from Spring's AccessDeniedException (which is thrown by @PreAuthorize).
 * Use this for custom authorization checks in service layer code, e.g.:
 * - A company user trying to access another tenant's data
 * - A CLIENT user trying to access ADMIN-only operations
 */
public class UnauthorizedAccessException extends RuntimeException {
    public UnauthorizedAccessException(String message) {
        super(message);
    }
}
