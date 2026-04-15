package com.multitenant.app.module.company.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Tenant-scoped entity representing a role within a company (tenant).
 *
 * This entity maps to the "roles" table in each tenant's isolated database schema.
 * Hibernate's multi-tenancy routing ensures all queries hit the correct tenant schema
 * automatically, so roles are inherently scoped to the tenant without a tenant ID column.
 *
 * Roles come in two varieties, distinguished by the {@code isSystemRole} flag:
 *
 * 1. System roles (isSystemRole = true):
 *    Built-in roles such as ADMIN, ROLE1, ROLE2 that are seeded during tenant
 *    provisioning. These roles cannot be created by users and are expected to exist
 *    in every tenant's schema. They provide the baseline permission structure.
 *
 * 2. Custom roles (isSystemRole = false):
 *    Roles created at runtime by a tenant's admin through the role management API.
 *    Custom roles follow a dual-write pattern -- they are created in BOTH the local
 *    database and Keycloak to keep authorization consistent across both systems.
 *
 * Note: Unlike {@link CompanyUser}, this entity does NOT extend AuditEntity.
 * It only tracks creation time via {@code @CreationTimestamp}, since roles are
 * typically created once and rarely modified.
 */
@Entity
@Table(name = "roles")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Role {

    /**
     * Primary key, auto-generated as a UUID.
     * Used internally for role lookups and for the many-to-many join with CompanyUser.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * The role name (e.g., "ADMIN", "ROLE1", "MANAGER", "VIEWER").
     * Must be unique within the tenant's schema and limited to 50 characters.
     *
     * Convention: role names are uppercase with underscores (e.g., "FINANCE_ADMIN").
     * This convention is enforced at the DTO level via regex validation in AddRoleRequest.
     *
     * The name is also used as the role identifier in Keycloak, so it must match
     * exactly between the local DB and Keycloak for the dual-write pattern to work.
     */
    @Column(unique = true, nullable = false, length = 50)
    private String name;

    /** Optional human-readable description of what this role grants, up to 255 characters. */
    @Column(length = 255)
    private String description;

    /**
     * Flag that distinguishes system-seeded roles from custom roles.
     *
     * - true:  System role, seeded during tenant schema provisioning (e.g., ADMIN, ROLE1, ROLE2).
     *          These should not be deleted or renamed by tenant admins.
     * - false: Custom role, created by a tenant admin at runtime via the API.
     *          These can be freely managed by the admin.
     *
     * Defaults to false via @Builder.Default, since roles created through the API
     * are always custom roles. System roles are set to true during the seeding process.
     */
    @Column(name = "is_system_role", nullable = false)
    @Builder.Default
    private boolean isSystemRole = false;

    /**
     * Timestamp of when this role was created, automatically set by Hibernate
     * on first persist. Marked as non-updatable so it cannot be changed after creation.
     */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
