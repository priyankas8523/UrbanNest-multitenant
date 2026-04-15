package com.multitenant.app.module.company.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for creating a new custom role within a tenant.
 *
 * This DTO is validated by Spring's @Valid annotation at the controller layer.
 * Only the role name is required; description is optional.
 *
 * Roles created through this request are always custom roles (isSystemRole = false).
 * System roles (ADMIN, ROLE1, ROLE2) are seeded during tenant provisioning and
 * cannot be created via this endpoint.
 *
 * The role name must follow strict formatting rules (see the @Pattern annotation)
 * because the same name is used as the role identifier in both the local database
 * and Keycloak. Consistent naming avoids mismatches in the dual-write pattern.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AddRoleRequest {

    /**
     * The name for the new role.
     *
     * Validation rules:
     * - @NotBlank: Must not be null, empty, or whitespace-only.
     * - @Size(min=2, max=50): Must be between 2 and 50 characters long.
     * - @Pattern("^[A-Z][A-Z0-9_]*$"): Enforces uppercase naming convention:
     *     - Must start with an uppercase letter (A-Z).
     *     - Remaining characters can be uppercase letters, digits, or underscores.
     *     - Examples of valid names: "ADMIN", "ROLE1", "FINANCE_ADMIN", "HR_MANAGER_2".
     *     - Examples of invalid names: "admin" (lowercase), "1ROLE" (starts with digit),
     *       "MY-ROLE" (contains hyphen), "A" (too short).
     *
     * This strict format ensures the role name is compatible with Keycloak's role
     * naming and can be safely used in @PreAuthorize expressions and JWT claims.
     */
    @NotBlank(message = "Role name is required")
    @Size(min = 2, max = 50, message = "Role name must be between 2 and 50 characters")
    @Pattern(regexp = "^[A-Z][A-Z0-9_]*$", message = "Role name must be uppercase, start with a letter, and contain only letters, numbers, and underscores")
    private String name;

    /**
     * Optional description of the role's purpose (e.g., "Can view and edit financial reports").
     * Limited to 255 characters. Stored in both the local database and passed to Keycloak
     * during role creation.
     */
    @Size(max = 255, message = "Description must not exceed 255 characters")
    private String description;
}
