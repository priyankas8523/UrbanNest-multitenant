package com.multitenant.app.module.company.repository;

import com.multitenant.app.module.company.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link Role} entities.
 *
 * Like all repositories in this multitenant application, queries are automatically
 * scoped to the current tenant's database schema via Hibernate's multi-tenancy
 * configuration. Each tenant has its own "roles" table containing both system-seeded
 * roles (ADMIN, ROLE1, ROLE2) and any custom roles created by the tenant's admin.
 *
 * This means {@code findAll()} returns only the roles belonging to the current tenant,
 * and {@code existsByName()} checks for duplicates only within the current tenant's schema.
 */
@Repository
public interface RoleRepository extends JpaRepository<Role, UUID> {

    /**
     * Find a role by its exact name within the current tenant's schema.
     * Role names are unique per tenant (enforced by a unique constraint on the "roles" table).
     *
     * @param name the role name to search for (e.g., "ADMIN", "FINANCE_MANAGER")
     * @return the matching Role, or empty if no role with that name exists in this tenant
     */
    Optional<Role> findByName(String name);

    /**
     * Check whether a role with the given name already exists in this tenant's schema.
     * Used as a guard before creating a new custom role to prevent duplicate name violations.
     *
     * @param name the role name to check
     * @return true if a role with this name exists in the current tenant's schema
     */
    boolean existsByName(String name);
}
