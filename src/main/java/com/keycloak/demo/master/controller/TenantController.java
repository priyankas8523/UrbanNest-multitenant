package com.keycloak.demo.master.controller;

import com.keycloak.demo.common.constants.AppConstants;
import com.keycloak.demo.common.constants.RoleConstants;
import com.keycloak.demo.common.dto.ApiResponse;
import com.keycloak.demo.common.dto.PagedResponse;
import com.keycloak.demo.master.dto.TenantDto;
import com.keycloak.demo.master.service.TenantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * TenantController -- REST endpoints for managing tenant (company) records.
 *
 * IMPORTANT: Every endpoint in this controller is restricted to SUPER_ADMIN role only.
 * SUPER_ADMIN is the platform operator -- the person managing all tenants, not a tenant's
 * own admin. A company's ADMIN role has no access to these endpoints.
 *
 * This controller handles READ and STATUS CHANGE operations on existing tenants.
 * It does NOT handle tenant creation/registration -- that is done through a separate
 * registration endpoint that calls TenantProvisioningService.
 *
 * The base path is defined in AppConstants.TENANT_PATH (likely something like "/api/v1/tenants").
 *
 * All responses are wrapped in ApiResponse<T> for consistent API structure:
 *   { "success": true, "data": {...}, "message": "..." }
 *
 * Swagger/OpenAPI annotations (@Tag, @Operation) provide documentation that appears
 * in the Swagger UI at /swagger-ui.html, making it easy to test and explore the API.
 */
@RestController
@RequestMapping(AppConstants.TENANT_PATH)
@RequiredArgsConstructor
@Tag(name = "Tenant Management", description = "SUPER_ADMIN operations for managing tenants")
public class TenantController {

    private final TenantService tenantService;

    /**
     * GET /tenants -- List all tenants with pagination.
     *
     * Used by the SUPER_ADMIN dashboard to browse all registered companies.
     * Supports standard Spring pagination parameters: ?page=0&size=20&sort=companyName,asc
     * Default page size is 20 if not specified (set by @PageableDefault).
     *
     * Returns a PagedResponse wrapper that includes pagination metadata
     * (total elements, total pages, current page) alongside the tenant data.
     */
    @GetMapping
    @PreAuthorize(RoleConstants.HAS_SUPER_ADMIN)
    @Operation(summary = "List all tenants")
    public ResponseEntity<ApiResponse<PagedResponse<TenantDto>>> listTenants(
            @PageableDefault(size = 20) Pageable pageable) {
        Page<TenantDto> tenants = tenantService.listTenants(pageable);
        return ResponseEntity.ok(ApiResponse.success(PagedResponse.from(tenants)));
    }

    /**
     * GET /tenants/{tenantId} -- Get details of a specific tenant by its slug.
     *
     * The {tenantId} path variable is the human-readable slug (e.g., "acme-corp"),
     * NOT the internal UUID. This keeps URLs clean and predictable.
     * Returns 404 (via TenantNotFoundException) if the slug doesn't match any tenant.
     */
    @GetMapping("/{tenantId}")
    @PreAuthorize(RoleConstants.HAS_SUPER_ADMIN)
    @Operation(summary = "Get tenant by ID")
    public ResponseEntity<ApiResponse<TenantDto>> getTenant(@PathVariable String tenantId) {
        return ResponseEntity.ok(ApiResponse.success(tenantService.getTenantByTenantId(tenantId)));
    }

    /**
     * PUT /tenants/{tenantId}/suspend -- Suspend a tenant.
     *
     * Suspending a tenant blocks all their users from accessing the application.
     * The data is preserved (not deleted), and the tenant can be re-activated later.
     * Typical reasons: non-payment, terms of service violation, security investigation.
     *
     * Uses PUT (not PATCH) because this is an idempotent status change operation.
     * Returns 200 with a success message, or 400 if the tenant is already DELETED.
     */
    @PutMapping("/{tenantId}/suspend")
    @PreAuthorize(RoleConstants.HAS_SUPER_ADMIN)
    @Operation(summary = "Suspend a tenant")
    public ResponseEntity<ApiResponse<Void>> suspendTenant(@PathVariable String tenantId) {
        tenantService.suspendTenant(tenantId);
        return ResponseEntity.ok(ApiResponse.success(null, "Tenant suspended successfully"));
    }

    /**
     * PUT /tenants/{tenantId}/activate -- Re-activate a suspended tenant.
     *
     * Restores full access for the tenant's users. Can be used after resolving
     * whatever issue caused the suspension.
     * Returns 200 with a success message, or 400 if the tenant is DELETED (irreversible).
     */
    @PutMapping("/{tenantId}/activate")
    @PreAuthorize(RoleConstants.HAS_SUPER_ADMIN)
    @Operation(summary = "Activate a tenant")
    public ResponseEntity<ApiResponse<Void>> activateTenant(@PathVariable String tenantId) {
        tenantService.activateTenant(tenantId);
        return ResponseEntity.ok(ApiResponse.success(null, "Tenant activated successfully"));
    }
}
