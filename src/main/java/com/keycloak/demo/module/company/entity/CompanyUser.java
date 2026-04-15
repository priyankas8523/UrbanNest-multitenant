package com.multitenant.app.module.company.entity;

import com.multitenant.app.common.audit.AuditEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Tenant-scoped entity representing a user within a company (tenant).
 *
 * This entity maps to the "company_users" table, which exists in each tenant's
 * isolated database schema. Hibernate's multi-tenancy support automatically routes
 * all queries to the correct tenant schema based on the current tenant context,
 * so there is no need for a tenant identifier column on this table.
 *
 * Extends {@link AuditEntity} to inherit automatic audit fields (createdAt, updatedAt,
 * createdBy, updatedBy) that track who created/modified the record and when.
 *
 * Each CompanyUser is linked to an external Keycloak identity via {@code keycloakId},
 * enabling authentication to be handled by Keycloak while authorization and profile
 * data are managed locally within the tenant's schema.
 *
 * Relationships:
 * - Many-to-many with {@link Role} through the "company_user_roles" join table.
 *   A user can have multiple roles (e.g., ADMIN + custom roles), and a role can
 *   be assigned to multiple users.
 */
@Entity
@Table(name = "company_users")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompanyUser extends AuditEntity {

    /**
     * Primary key, auto-generated as a UUID.
     * This is the internal identifier used within the tenant's schema.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * The unique identifier from Keycloak (the "sub" claim in the JWT).
     * This links the local CompanyUser record to the corresponding Keycloak user.
     * Used to look up the user's profile when they authenticate via JWT -- the
     * controller extracts jwt.getSubject() and queries by this field.
     *
     * Marked unique and non-nullable because every company user must have exactly
     * one corresponding Keycloak identity.
     */
    @Column(name = "keycloak_id", unique = true, nullable = false)
    private String keycloakId;

    /**
     * The user's email address. Serves as a natural identifier for the user
     * and must be unique within the tenant's schema. Used for display and
     * for looking up users by email when needed.
     */
    @Column(unique = true, nullable = false)
    private String email;

    /** User's first name, limited to 100 characters. */
    @Column(name = "first_name", length = 100)
    private String firstName;

    /** User's last name, limited to 100 characters. */
    @Column(name = "last_name", length = 100)
    private String lastName;

    /** User's phone number, limited to 20 characters to accommodate international formats. */
    @Column(length = 20)
    private String phone;

    /**
     * Soft-delete flag. When false, the user is considered deactivated.
     *
     * Deactivation is a two-step process: this flag is set to false in the local DB,
     * AND the user is disabled in Keycloak (via KeycloakUserService.disableUser).
     * This ensures the user cannot log in (Keycloak side) and is filtered out of
     * active user listings (local DB side).
     *
     * Defaults to true (active) when a new user is created via the @Builder.Default annotation.
     */
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean isActive = true;

    /**
     * The set of roles assigned to this user within the tenant.
     *
     * This is a many-to-many relationship managed through the "company_user_roles"
     * join table, which has two foreign key columns: user_id and role_id.
     *
     * FetchType.LAZY ensures roles are only loaded from the database when explicitly
     * accessed (e.g., user.getRoles()), avoiding unnecessary joins on every user query.
     *
     * Initialized as an empty HashSet via @Builder.Default to prevent NullPointerException
     * when adding roles to a newly created user before the first persistence.
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "company_user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    @Builder.Default
    private Set<Role> roles = new HashSet<>();
}
