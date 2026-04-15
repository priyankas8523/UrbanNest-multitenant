package com.keycloak.demo.common.exception;

/**
 * Thrown when the system cannot determine which tenant a request belongs to.
 *
 * Triggers: 400 BAD_REQUEST response via GlobalExceptionHandler.
 *
 * Common scenarios:
 * - Client registration request is missing the X-Tenant-ID header
 * - X-Tenant-ID header is present but blank/empty
 * - Authenticated request has a JWT without the tenant_id claim
 */
public class TenantResolutionException extends RuntimeException {
    public TenantResolutionException(String message) {
        super(message);
    }
}
