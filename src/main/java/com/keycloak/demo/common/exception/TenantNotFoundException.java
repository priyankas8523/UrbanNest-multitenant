package com.multitenant.app.common.exception;

/**
 * Thrown when a tenant ID doesn't match any registered tenant in the system.
 *
 * Triggers: 404 NOT_FOUND response via GlobalExceptionHandler.
 *
 * Common scenarios:
 * - Client tries to register with an invalid X-Tenant-ID header
 * - SUPER_ADMIN looks up a tenant that doesn't exist
 * - JWT contains a tenant_id for a tenant that was deleted
 */
public class TenantNotFoundException extends RuntimeException {
    public TenantNotFoundException(String tenantId) {
        super("Tenant not found: " + tenantId);
    }
}
