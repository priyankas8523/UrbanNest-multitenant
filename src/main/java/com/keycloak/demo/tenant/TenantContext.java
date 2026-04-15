package com.keycloak.demo.tenant;

import lombok.extern.slf4j.Slf4j;

/**
 * Thread-local storage for the current tenant ID.
 *
 * THIS IS THE HEART OF MULTITENANCY. Every incoming request gets a tenant ID
 * (from JWT or header), and it's stored here so Hibernate knows which schema to query.
 *
 * HOW IT WORKS:
 *   1. TenantFilter receives HTTP request
 *   2. Extracts tenant_id from JWT claim (or X-Tenant-ID header)
 *   3. Calls TenantContext.setTenantId("acme-corp")
 *   4. Hibernate's CurrentTenantIdentifierResolverImpl calls TenantContext.getTenantId()
 *      to determine the schema: "tenant_acme-corp"
 *   5. SchemaMultiTenantConnectionProvider sets the PostgreSQL search_path to that schema
 *   6. All JPA queries during this request hit the correct tenant's tables
 *   7. After the request completes, TenantFilter calls TenantContext.clear() in a finally block
 *
 * WHY ThreadLocal: Each HTTP request runs on its own thread. ThreadLocal ensures that
 * tenant ID from Request A doesn't leak into Request B, even under high concurrency.
 *
 * WHY InheritableThreadLocal: If a service uses @Async (spawns a child thread),
 * the child thread inherits the parent's tenant context. Without this, async operations
 * would lose track of which tenant they belong to.
 *
 * CRITICAL: Always call clear() after the request completes. Thread pools reuse threads,
 * so a leftover tenant ID from a previous request could cause data to be written
 * to the wrong tenant's schema. The TenantFilter handles this in its finally block.
 */
@Slf4j
public final class TenantContext {

    // InheritableThreadLocal so @Async child threads inherit the tenant context
    private static final ThreadLocal<String> CURRENT_TENANT = new InheritableThreadLocal<>();

    private TenantContext() {}  // Utility class - no instantiation

    /** Set the current tenant for this request's thread */
    public static void setTenantId(String tenantId) {
        log.debug("Setting tenant context to: {}", tenantId);
        CURRENT_TENANT.set(tenantId);
    }

    /** Get the current tenant ID (returns null if no tenant context is set) */
    public static String getTenantId() {
        return CURRENT_TENANT.get();
    }

    /** MUST be called after every request to prevent tenant leakage between requests */
    public static void clear() {
        log.debug("Clearing tenant context");
        CURRENT_TENANT.remove();
    }
}
