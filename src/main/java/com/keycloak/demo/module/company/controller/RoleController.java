package com.multitenant.app.module.company.controller;

import com.multitenant.app.common.constants.AppConstants;
import com.multitenant.app.common.constants.RoleConstants;
import com.multitenant.app.common.dto.ApiResponse;
import com.multitenant.app.module.company.dto.AddRoleRequest;
import com.multitenant.app.module.company.dto.AssignRoleRequest;
import com.multitenant.app.module.company.dto.RoleDto;
import com.multitenant.app.module.company.service.RoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for role management within the current tenant.
 *
 * Base path: {@link AppConstants#COMPANY_PATH} + "/roles" (e.g., "/api/company/roles").
 *
 * ALL endpoints in this controller are restricted to users with the ADMIN role.
 * This is enforced per-method via @PreAuthorize(RoleConstants.HAS_ADMIN), which
 * checks the ADMIN role in the authenticated user's JWT claims.
 *
 * Endpoints:
 * - GET  /roles:                  List all roles (system + custom) in this tenant.
 * - POST /roles:                  Create a new custom role (dual-write to Keycloak + local DB).
 * - POST /roles/assign:           Assign an existing role to a user (dual-write).
 * - DELETE /roles/{roleId}/users/{userId}: Remove a role from a user (dual-write).
 *
 * Dual-write pattern:
 * Every mutation (create, assign, remove) writes to both Keycloak and the local
 * database. Keycloak needs the role for JWT token claims (used by @PreAuthorize),
 * while the local DB needs it for application queries and UI display. See
 * {@link RoleService} for detailed explanation of the dual-write approach.
 *
 * Tenant scoping:
 * The tenant is resolved from the incoming request before reaching this controller.
 * All database operations are automatically scoped to the current tenant's schema
 * by Hibernate's multi-tenancy support.
 */
@RestController
@RequestMapping(AppConstants.COMPANY_PATH + "/roles")
@RequiredArgsConstructor
@Tag(name = "Role Management", description = "ADMIN operations for managing company roles")
public class RoleController {

    private final RoleService roleService;

    /**
     * List all roles available in the current tenant's schema.
     *
     * Returns both system-seeded roles (ADMIN, ROLE1, ROLE2) and custom roles
     * created by the tenant admin. Each RoleDto includes the isSystemRole flag
     * so the frontend can distinguish between them (e.g., to prevent deletion
     * of system roles in the UI).
     *
     * @return list of all roles wrapped in ApiResponse
     */
    @GetMapping
    @PreAuthorize(RoleConstants.HAS_ADMIN)
    @Operation(summary = "List all roles in this tenant")
    public ResponseEntity<ApiResponse<List<RoleDto>>> listRoles() {
        return ResponseEntity.ok(ApiResponse.success(roleService.listRoles()));
    }

    /**
     * Create a new custom role.
     *
     * The request body is validated via @Valid, enforcing:
     * - Role name is required, 2-50 chars, uppercase with letters/digits/underscores.
     * - Description is optional, max 255 chars.
     *
     * The role is created in Keycloak first (as a realm role), then in the local
     * database with isSystemRole = false. Returns HTTP 201 Created on success.
     *
     * @param request the validated role creation request
     * @return the newly created role as a DTO, with HTTP 201 status
     */
    @PostMapping
    @PreAuthorize(RoleConstants.HAS_ADMIN)
    @Operation(summary = "Create a new custom role")
    public ResponseEntity<ApiResponse<RoleDto>> createRole(
            @Valid @RequestBody AddRoleRequest request) {
        RoleDto role = roleService.createRole(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(role, "Role created successfully"));
    }

    /**
     * Assign an existing role to a company user.
     *
     * The request body provides both userId and roleId. The service performs
     * a dual-write: assigns the role in Keycloak (so it appears in the user's
     * JWT on next login) and adds a row to the company_user_roles join table.
     *
     * Returns an error if the user already has the specified role.
     *
     * @param request the validated assignment request containing userId and roleId
     * @return success message wrapped in ApiResponse
     */
    @PostMapping("/assign")
    @PreAuthorize(RoleConstants.HAS_ADMIN)
    @Operation(summary = "Assign a role to a user")
    public ResponseEntity<ApiResponse<Void>> assignRole(
            @Valid @RequestBody AssignRoleRequest request) {
        roleService.assignRoleToUser(request);
        return ResponseEntity.ok(ApiResponse.success(null, "Role assigned successfully"));
    }

    /**
     * Remove a role from a company user.
     *
     * Uses a RESTful URL pattern: DELETE /roles/{roleId}/users/{userId}.
     * The service performs a dual-write: removes the role from the user in
     * Keycloak (so it is excluded from the user's next JWT) and deletes the
     * corresponding row from the company_user_roles join table.
     *
     * Note: This only removes the role-user association; it does not delete
     * the role itself. The role remains available for assignment to other users.
     *
     * Returns an error if the user does not currently have the specified role.
     *
     * @param roleId the UUID of the role to remove
     * @param userId the UUID of the user to remove the role from
     * @return success message wrapped in ApiResponse
     */
    @DeleteMapping("/{roleId}/users/{userId}")
    @PreAuthorize(RoleConstants.HAS_ADMIN)
    @Operation(summary = "Remove a role from a user")
    public ResponseEntity<ApiResponse<Void>> removeRole(
            @PathVariable UUID roleId,
            @PathVariable UUID userId) {
        roleService.removeRoleFromUser(roleId, userId);
        return ResponseEntity.ok(ApiResponse.success(null, "Role removed successfully"));
    }
}
