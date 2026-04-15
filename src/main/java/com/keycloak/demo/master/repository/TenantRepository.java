package com.multitenant.app.master.repository;

import com.multitenant.app.common.enums.TenantStatus;
import com.multitenant.app.master.entity.Tenant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for the public.tenants table -- the master registry of all companies.
 *
 * This repository operates on the public schema (hardcoded in the Tenant entity's @Table annotation),
 * so it is NOT affected by the dynamic tenant schema routing. It always hits the master database.
 *
 * Used by:
 *   - TenantService: SUPER_ADMIN CRUD operations (list, get, suspend, activate).
 *   - TenantProvisioningService: creating new tenant records during company registration.
 *   - Security/connection filters: resolving tenant_id from JWT claims to find the schema_name.
 */
@Repository
public interface TenantRepository extends JpaRepository<Tenant, UUID> {

    /**
     * Look up a tenant by its human-readable slug (e.g., "acme-corp").
     * This is the primary lookup method used throughout the app because tenantId
     * is what appears in JWT claims, API paths, and Keycloak attributes.
     * Returns Optional because the tenant may not exist (invalid/expired token, typo in URL).
     */
    Optional<Tenant> findByTenantId(String tenantId);

    /**
     * Quick existence check by tenant slug. Used during provisioning to prevent
     * duplicate registrations -- if a company name generates a slug that already exists,
     * registration is rejected. Faster than findByTenantId() because it only does a COUNT
     * query rather than loading the full entity.
     */
    boolean existsByTenantId(String tenantId);

    /**
     * Check if an email is already registered as a tenant owner. Enforces the rule that
     * one person (email) can only own one tenant. Called during provisioning validation
     * to give a clear error message instead of a DB constraint violation.
     */
    boolean existsByOwnerEmail(String ownerEmail);

    /**
     * Paginated query to list tenants filtered by status. Useful for SUPER_ADMIN dashboards --
     * for example, showing all SUSPENDED tenants, or all ACTIVE tenants.
     * Spring Data automatically generates the WHERE clause and pagination SQL.
     */
    Page<Tenant> findByStatus(TenantStatus status, Pageable pageable);
}
