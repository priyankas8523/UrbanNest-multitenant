package com.multitenant.app.module.company.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Request DTO for assigning a role to a company user.
 *
 * Used by the POST /roles/assign endpoint. The admin provides both the target
 * user's ID and the role's ID. The service layer then performs a dual-write:
 * assigning the role in both the local database (company_user_roles join table)
 * and in Keycloak (so the role appears in the user's JWT on next authentication).
 *
 * Both IDs reference records within the current tenant's schema -- there is no
 * risk of cross-tenant assignment because Hibernate's multi-tenancy ensures
 * lookups are scoped to the current tenant.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AssignRoleRequest {

    /**
     * The UUID of the company user to whom the role will be assigned.
     * Must reference an existing user in the current tenant's company_users table.
     */
    @NotNull(message = "User ID is required")
    private UUID userId;

    /**
     * The UUID of the role to assign.
     * Must reference an existing role in the current tenant's roles table.
     * Can be either a system role or a custom role.
     */
    @NotNull(message = "Role ID is required")
    private UUID roleId;
}
