package com.multitenant.app.master.service;

import com.multitenant.app.common.constants.AppConstants;
import com.multitenant.app.common.constants.RoleConstants;
import com.multitenant.app.common.enums.TenantStatus;
import com.multitenant.app.common.exception.BusinessException;
import com.multitenant.app.iam.IamService;
import com.multitenant.app.master.dto.TenantDto;
import com.multitenant.app.master.dto.TenantRegistrationRequest;
import com.multitenant.app.master.entity.Tenant;
import com.multitenant.app.master.repository.TenantRepository;
import com.multitenant.app.tenant.TenantContext;
import com.multitenant.app.tenant.TenantSchemaInitializer;
import com.multitenant.app.module.company.entity.CompanyUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * TenantProvisioningService - Orchestrates the TWO-PHASE tenant onboarding flow.
 *
 * PHASE 1: registerTenant() - Lightweight registration (FREE, instant)
 *   Creates: Keycloak user + group + public.tenants record (status = REGISTERED)
 *   Does NOT create: database schema (no tenant_* schema yet)
 *   Admin can: login, view dashboard, buy subscription plan
 *   Admin cannot: manage users, roles, clients (no schema = no tables)
 *
 * PHASE 2: provisionTenantSchema() - Schema creation (triggered by subscription purchase)
 *   Creates: PostgreSQL schema (tenant_{slug}) + runs Liquibase migrations
 *   Creates: CompanyUser record in tenant schema
 *   Updates: tenant status from REGISTERED -> PROVISIONING -> ACTIVE
 *   Admin can now: do everything (manage users, roles, clients)
 *
 * WHY TWO PHASES:
 *   - No wasted schemas for companies that register but never subscribe
 *   - Faster registration (Keycloak + DB insert vs Keycloak + DB + schema + migrations)
 *   - Clear billing gate: schema = paid resource, only created after plan purchase
 *   - Industry standard SaaS pattern (Stripe, Slack, etc.)
 *
 * Both phases have saga compensation: if any step fails, previously completed steps are rolled back.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantProvisioningService {

    private final TenantRepository tenantRepository;
    private final TenantSchemaInitializer schemaInitializer;
    private final IamService iamService;
    private final TenantUserProvisioner tenantUserProvisioner;

    /**
     * PHASE 1: Register a company - creates Keycloak identity + public schema record ONLY.
     * No database schema is created yet. Admin gets status = REGISTERED.
     *
     * Called by: POST /v1/auth/company/register
     */
    @Transactional
    public TenantDto registerTenant(TenantRegistrationRequest request) {
        log.info("Phase 1: Registering company '{}'", request.getCompanyName());

        // Generate tenant slug from company name (e.g., "Acme Corp!" -> "acme-corp")
        String tenantId = generateTenantSlug(request.getCompanyName());
        if (tenantRepository.existsByTenantId(tenantId)) {
            throw new BusinessException("A company with a similar name already exists");
        }
        if (tenantRepository.existsByOwnerEmail(request.getAdminEmail())) {
            throw new BusinessException("Email already registered as a tenant owner");
        }

        String schemaName = AppConstants.TENANT_SCHEMA_PREFIX + tenantId;
        Tenant tenant = null;
        String keycloakUserId = null;
        String groupId = null;

        try {
            // Step 1: Create tenant record in public schema (status = REGISTERED, no schema yet)
            tenant = Tenant.builder()
                    .tenantId(tenantId)
                    .companyName(request.getCompanyName())
                    .schemaName(schemaName)
                    .status(TenantStatus.REGISTERED)   // NEW: registered but not provisioned
                    .ownerEmail(request.getAdminEmail())
                    .build();
            tenant = tenantRepository.save(tenant);

            // Step 2: Create Keycloak group for this tenant
            groupId = iamService.createGroup(tenantId);

            // Step 3: Create admin user in Keycloak with tenant_id attribute
            keycloakUserId = iamService.createUser(
                    request.getAdminEmail(),
                    request.getPassword(),
                    request.getFirstName(),
                    request.getLastName(),
                    Map.of("tenant_id", tenantId, "portal_type", "company")
            );

            // Step 4: Add user to group + assign ADMIN role
            iamService.addUserToGroup(keycloakUserId, groupId);
            iamService.assignRealmRole(keycloakUserId, RoleConstants.ADMIN);

            // Step 5: Link Keycloak user ID to tenant record
            tenant.setOwnerKeycloakId(keycloakUserId);
            tenant = tenantRepository.save(tenant);

            log.info("Phase 1 complete: Company '{}' registered (status=REGISTERED, no schema yet)", tenantId);

            return mapToDto(tenant);

        } catch (Exception ex) {
            log.error("Phase 1 failed for '{}'. Compensating.", tenantId, ex);
            compensateRegistration(tenant, keycloakUserId, groupId);
            throw new BusinessException("Registration failed: " + ex.getMessage());
        }
    }

    /**
     * PHASE 2: Provision tenant schema - called AFTER subscription purchase.
     * Creates the actual database schema, runs migrations, creates CompanyUser record.
     * Transitions tenant from REGISTERED -> PROVISIONING -> ACTIVE.
     *
     * Called by: SubscriptionService.purchasePlan()
     */
    @Transactional
    public TenantDto provisionTenantSchema(String tenantId) {
        log.info("Phase 2: Provisioning schema for tenant '{}'", tenantId);

        Tenant tenant = tenantRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new BusinessException("Tenant not found: " + tenantId));

        // Guard: only REGISTERED tenants can be provisioned
        if (tenant.getStatus() != TenantStatus.REGISTERED) {
            throw new BusinessException("Tenant '" + tenantId + "' is not in REGISTERED state. Current: " + tenant.getStatus());
        }

        try {
            // Step 1: Mark as PROVISIONING
            tenant.setStatus(TenantStatus.PROVISIONING);
            tenantRepository.save(tenant);

            // Step 2: Create PostgreSQL schema + run Liquibase migrations
            schemaInitializer.createSchema(tenantId);

            // Step 3: Create CompanyUser record in the new tenant schema.
            // TenantContext must be set BEFORE calling tenantUserProvisioner.saveAdminUser(),
            // which opens a REQUIRES_NEW transaction. Hibernate reads TenantContext when the
            // new transaction starts to route the INSERT to the correct tenant schema.
            TenantContext.setTenantId(tenantId);
            try {
                CompanyUser companyUser = CompanyUser.builder()
                        .keycloakId(tenant.getOwnerKeycloakId())
                        .email(tenant.getOwnerEmail())
                        .firstName("Admin")  // Default - admin can update later
                        .lastName(tenant.getCompanyName())
                        .isActive(true)
                        .build();
                // Delegate to a separate bean so @Transactional(REQUIRES_NEW) is applied
                // via Spring AOP proxy (self-invocation would bypass the proxy).
                tenantUserProvisioner.saveAdminUser(companyUser);
            } finally {
                TenantContext.clear();
            }

            // Step 4: Mark as ACTIVE - tenant is fully operational
            tenant.setStatus(TenantStatus.ACTIVE);
            tenant = tenantRepository.save(tenant);

            log.info("Phase 2 complete: Tenant '{}' provisioned and ACTIVE", tenantId);
            return mapToDto(tenant);

        } catch (Exception ex) {
            log.error("Phase 2 failed for '{}'. Rolling back schema.", tenantId, ex);
            // Compensation: drop schema if it was partially created
            try { schemaInitializer.dropSchema(tenantId); } catch (Exception e) { log.error("Schema drop failed", e); }
            // Revert status back to REGISTERED so admin can retry
            tenant.setStatus(TenantStatus.REGISTERED);
            tenantRepository.save(tenant);
            throw new BusinessException("Schema provisioning failed: " + ex.getMessage());
        }
    }

    /** Compensation for Phase 1 failures - reverse-order cleanup. */
    private void compensateRegistration(Tenant tenant, String keycloakUserId, String groupId) {
        try { if (keycloakUserId != null) iamService.deleteUser(keycloakUserId); }
        catch (Exception e) { log.error("Compensation: failed to delete Keycloak user", e); }

        try { if (groupId != null) iamService.deleteGroup(groupId); }
        catch (Exception e) { log.error("Compensation: failed to delete Keycloak group", e); }

        try { if (tenant != null && tenant.getId() != null) tenantRepository.delete(tenant); }
        catch (Exception e) { log.error("Compensation: failed to delete tenant record", e); }
    }

    /** Convert company name to underscore slug: "Acme Corp!" -> "acme_corp"
     *  Underscores are used (not hyphens) because PostgreSQL schema names with hyphens
     *  require quoting everywhere, which causes issues with Liquibase and Hibernate. */
    private String generateTenantSlug(String companyName) {
        return companyName
                .toLowerCase().trim()
                .replaceAll("[^a-z0-9\\s]", "")
                .replaceAll("[\\s]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "");
    }

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
