package com.multitenant.app.master.service;

import com.multitenant.app.common.enums.TenantStatus;
import com.multitenant.app.common.exception.BusinessException;
import com.multitenant.app.common.exception.TenantNotFoundException;
import com.multitenant.app.master.dto.TenantDto;
import com.multitenant.app.master.entity.Tenant;
import com.multitenant.app.master.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * TenantService -- CRUD operations on tenant records in the public schema.
 *
 * This service is the "admin panel backend" for managing tenants. It does NOT handle
 * provisioning (schema creation, Keycloak setup) -- that is TenantProvisioningService's job.
 * This service only reads and updates existing tenant records.
 *
 * All methods here are called by TenantController, which is restricted to SUPER_ADMIN role.
 * Regular tenant users and ADMIN users never call these endpoints.
 *
 * Key design decisions:
 *   - readOnly = true on queries: tells Hibernate to skip dirty-checking, improving performance.
 *   - Returns TenantDto (not the entity): prevents leaking internal fields like schema_name.
 *   - Status transitions are guarded: you cannot suspend or activate a DELETED tenant.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantService {

    private final TenantRepository tenantRepository;

    /**
     * Fetch a single tenant by its human-readable slug (e.g., "acme-corp").
     * Used by SUPER_ADMIN to view details of a specific company.
     * Throws TenantNotFoundException (maps to 404) if the slug doesn't match any record.
     */
    @Transactional(readOnly = true)
    public TenantDto getTenantByTenantId(String tenantId) {
        Tenant tenant = tenantRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new TenantNotFoundException(tenantId));
        return mapToDto(tenant);
    }

    /**
     * List all tenants with pagination. Returns a Page of DTOs so the controller can
     * build a PagedResponse with metadata (total count, page number, etc.).
     * Default page size is 20 (configured in the controller's @PageableDefault).
     */
    @Transactional(readOnly = true)
    public Page<TenantDto> listTenants(Pageable pageable) {
        return tenantRepository.findAll(pageable).map(this::mapToDto);
    }

    /**
     * Suspend a tenant -- sets status to SUSPENDED.
     *
     * When a tenant is suspended:
     *   - Their users can still authenticate with Keycloak (we don't disable Keycloak accounts).
     *   - BUT the application's security filter checks tenant status and blocks API access
     *     for SUSPENDED tenants, returning a 403 or similar error.
     *   - The tenant's database schema and data remain intact -- nothing is deleted.
     *
     * Guard rail: Cannot suspend a DELETED tenant because DELETED is a terminal state.
     * Suspending an already-SUSPENDED tenant is idempotent (just saves the same status).
     */
    @Transactional
    public void suspendTenant(String tenantId) {
        Tenant tenant = tenantRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new TenantNotFoundException(tenantId));

        if (tenant.getStatus() == TenantStatus.DELETED) {
            throw new BusinessException("Cannot suspend a deleted tenant");
        }

        tenant.setStatus(TenantStatus.SUSPENDED);
        tenantRepository.save(tenant);
        log.info("Tenant '{}' suspended", tenantId);
    }

    /**
     * Re-activate a tenant -- sets status back to ACTIVE.
     *
     * This restores full access for the tenant's users. Typically used after:
     *   - A billing issue is resolved.
     *   - A temporary suspension is lifted.
     *   - An investigation is completed.
     *
     * Guard rail: Cannot activate a DELETED tenant -- deletion is irreversible.
     */
    @Transactional
    public void activateTenant(String tenantId) {
        Tenant tenant = tenantRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new TenantNotFoundException(tenantId));

        if (tenant.getStatus() == TenantStatus.DELETED) {
            throw new BusinessException("Cannot activate a deleted tenant");
        }

        tenant.setStatus(TenantStatus.ACTIVE);
        tenantRepository.save(tenant);
        log.info("Tenant '{}' activated", tenantId);
    }

    /**
     * Quick existence check by tenant slug. Used by TenantProvisioningService
     * to validate that a generated slug doesn't collide with an existing tenant
     * before starting the provisioning process.
     */
    public boolean existsByTenantId(String tenantId) {
        return tenantRepository.existsByTenantId(tenantId);
    }

    /**
     * Quick existence check by email. Used during provisioning to enforce the
     * "one email = one tenant ownership" rule before attempting Keycloak user creation.
     */
    public boolean existsByOwnerEmail(String email) {
        return tenantRepository.existsByOwnerEmail(email);
    }

    /**
     * Maps the full Tenant entity to a safe TenantDto for API responses.
     * Deliberately omits: schemaName, ownerKeycloakId, subscriptionPlanId, updatedAt.
     * This ensures internal implementation details never leak to API consumers.
     */
    private TenantDto mapToDto(Tenant tenant) {
        return TenantDto.builder()
                .id(tenant.getId())
                .tenantId(tenant.getTenantId())
                .companyName(tenant.getCompanyName())
                .status(tenant.getStatus())
                .ownerEmail(tenant.getOwnerEmail())
                .createdAt(tenant.getCreatedAt())
                .build();
    }
}
