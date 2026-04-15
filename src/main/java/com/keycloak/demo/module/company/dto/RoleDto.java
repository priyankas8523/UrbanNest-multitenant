package com.multitenant.app.module.company.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Response DTO for role data returned to the client.
 *
 * Used by the role management endpoints to present role information.
 * This DTO includes the {@code isSystemRole} flag so the frontend can
 * differentiate between built-in system roles (ADMIN, ROLE1, ROLE2) and
 * custom roles created by the tenant admin. The frontend may use this
 * flag to disable delete/edit actions on system roles in the UI.
 *
 * The mapping from {@link com.multitenant.app.module.company.entity.Role}
 * to this DTO is done in RoleService.mapToDto().
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoleDto {

    /** Internal UUID of the role within the tenant's schema. */
    private UUID id;

    /** The role name (e.g., "ADMIN", "VIEWER", "FINANCE_MANAGER"). */
    private String name;

    /** Optional human-readable description of the role's purpose. */
    private String description;

    /**
     * Whether this is a system-seeded role (true) or a custom role (false).
     * System roles are created during tenant provisioning and should not be
     * deleted or renamed. Custom roles are created by the tenant admin at runtime.
     */
    private boolean isSystemRole;
}
