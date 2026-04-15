package com.keycloak.demo.common.constants;

/**
 * Role constants matching the roles defined in Keycloak's "multitenant" realm.
 *
 * ROLE HIERARCHY (highest to lowest privilege):
 *
 * SUPER_ADMIN  - Platform-level admin. Can manage all tenants (suspend, activate, list).
 *                Only assignable directly in Keycloak admin console, never via API.
 *
 * ADMIN        - Tenant-level admin. Auto-assigned when a company registers.
 *                Can manage users within their company, create custom roles, assign roles.
 *
 * ROLE1        - Default assignable role within a tenant. Typically used for
 *                managers or team leads who can view clients and resources.
 *
 * ROLE2        - Default assignable role within a tenant. Typically used for
 *                read-only access to tenant resources.
 *
 * CLIENT       - Assigned to users who register via the Client Portal.
 *                Can only view their own profile within their tenant.
 *
 * The HAS_* constants are SpEL expressions used with @PreAuthorize on controller methods.
 * Spring Security automatically adds the "ROLE_" prefix when checking, so hasRole('ADMIN')
 * matches a granted authority of "ROLE_ADMIN".
 */
public final class RoleConstants {

    private RoleConstants() {}

    // -- Role name strings (must match Keycloak realm roles exactly) --
    public static final String SUPER_ADMIN = "SUPER_ADMIN";
    public static final String ADMIN = "ADMIN";
    public static final String ROLE1 = "ROLE1";
    public static final String ROLE2 = "ROLE2";
    public static final String CLIENT = "CLIENT";

    // -- SpEL expressions for @PreAuthorize annotations --
    // Used on controller methods to enforce role-based access control

    // Only platform super admins (tenant management endpoints)
    public static final String HAS_SUPER_ADMIN = "hasRole('SUPER_ADMIN')";

    // Only tenant admins (user management, role management endpoints)
    public static final String HAS_ADMIN = "hasRole('ADMIN')";

    // Any company-side role can access (e.g., viewing client list)
    public static final String HAS_ADMIN_OR_ROLES = "hasAnyRole('ADMIN', 'ROLE1', 'ROLE2')";
}
