package com.keycloak.demo.module.company.repository;

import com.keycloak.demo.module.company.entity.CompanyUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link CompanyUser} entities.
 *
 * All queries executed through this repository are automatically scoped to the
 * current tenant's database schema. Hibernate's multi-tenancy support resolves
 * the correct schema at runtime based on the tenant context (typically extracted
 * from the incoming request's JWT or header), so no tenant filtering is needed
 * in the query methods themselves.
 *
 * This means calling {@code findAll()} returns only the company users belonging
 * to the current tenant, and saving a new CompanyUser inserts it into the
 * current tenant's "company_users" table.
 */
@Repository
public interface CompanyUserRepository extends JpaRepository<CompanyUser, UUID> {

    /**
     * Find a company user by their Keycloak identity.
     * Used primarily for profile lookups: the controller extracts the "sub" claim
     * from the JWT and passes it here to retrieve the authenticated user's profile.
     *
     * @param keycloakId the Keycloak user ID (JWT "sub" claim)
     * @return the matching CompanyUser, or empty if no user exists with that keycloakId
     */
    Optional<CompanyUser> findByKeycloakId(String keycloakId);

    /**
     * Find a company user by their email address.
     * Useful for lookups during user invitation or duplicate checks.
     *
     * @param email the email address to search for
     * @return the matching CompanyUser, or empty if not found
     */
    Optional<CompanyUser> findByEmail(String email);

    /**
     * Check whether a company user with the given email already exists in this tenant.
     * Used as a guard before creating new users to prevent duplicate email violations.
     *
     * @param email the email address to check
     * @return true if a user with this email exists in the current tenant's schema
     */
    boolean existsByEmail(String email);

    /**
     * Retrieve a paginated list of company users filtered by their active status.
     * Enables listing only active users (isActive=true) or only deactivated users
     * (isActive=false) with pagination support for large user bases.
     *
     * @param isActive true to list active users, false to list deactivated users
     * @param pageable pagination and sorting parameters
     * @return a page of CompanyUser entities matching the active status
     */
    Page<CompanyUser> findByIsActive(boolean isActive, Pageable pageable);
}
