package com.keycloak.demo.module.company.service;

import com.keycloak.demo.common.exception.ResourceNotFoundException;
import com.keycloak.demo.iam.IamService;
import com.keycloak.demo.module.company.dto.CompanyUserDto;
import com.keycloak.demo.module.company.entity.CompanyUser;
import com.keycloak.demo.module.company.repository.CompanyUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service layer for company user operations within the current tenant.
 *
 * Provides CRUD operations for company users, including profile lookup,
 * paginated user listing, and user activation/deactivation.
 *
 * Key design decisions:
 * - All repository queries are automatically scoped to the current tenant's schema
 *   by Hibernate's multi-tenancy, so this service does not need to handle tenant filtering.
 * - Deactivation and activation synchronize state between the local database AND Keycloak.
 *   The local isActive flag controls application-level visibility, while the Keycloak
 *   disable/enable controls whether the user can authenticate at all.
 * - Profile lookup uses the Keycloak ID (JWT "sub" claim) rather than the local UUID,
 *   because the authenticated user only knows their Keycloak identity from the token.
 *
 * Dependencies:
 * - {@link CompanyUserRepository}: tenant-scoped database access for company users.
 * - {@link KeycloakUserService}: Keycloak Admin API integration for enabling/disabling users.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CompanyService {

    private final CompanyUserRepository companyUserRepository;
    private final IamService iamService;  // Unified IAM facade (replaces KeycloakUserService)

    /**
     * Retrieve the current authenticated user's company profile.
     *
     * Called from the /profile endpoint, where the controller extracts the JWT "sub"
     * claim (Keycloak user ID) and passes it here. This allows any authenticated user
     * to view their own profile without needing to know their internal UUID.
     *
     * @param keycloakId the Keycloak user ID from the JWT subject claim
     * @return the user's profile as a DTO
     * @throws ResourceNotFoundException if no CompanyUser matches the keycloakId
     *         (this would indicate a data inconsistency between Keycloak and the local DB)
     */
    @Transactional(readOnly = true)
    public CompanyUserDto getProfileByKeycloakId(String keycloakId) {
        CompanyUser user = companyUserRepository.findByKeycloakId(keycloakId)
                .orElseThrow(() -> new ResourceNotFoundException("CompanyUser", "keycloakId", keycloakId));
        return mapToDto(user);
    }

    /**
     * List all company users in the current tenant with pagination.
     *
     * This is an admin-only operation (access control enforced at the controller level).
     * Returns all users regardless of active status; use findByIsActive for filtered results.
     *
     * @param pageable pagination and sorting parameters (default page size: 20)
     * @return a page of CompanyUserDto objects
     */
    @Transactional(readOnly = true)
    public Page<CompanyUserDto> listUsers(Pageable pageable) {
        return companyUserRepository.findAll(pageable).map(this::mapToDto);
    }

    /**
     * Retrieve a specific company user by their internal UUID.
     *
     * Admin-only operation for viewing detailed user information.
     *
     * @param userId the internal UUID of the company user
     * @return the user's data as a DTO
     * @throws ResourceNotFoundException if no user exists with the given ID
     */
    @Transactional(readOnly = true)
    public CompanyUserDto getUserById(UUID userId) {
        CompanyUser user = companyUserRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("CompanyUser", "id", userId));
        return mapToDto(user);
    }

    /**
     * Deactivate (soft-delete) a company user.
     *
     * This performs a two-step deactivation:
     * 1. Sets isActive = false in the local database, so the user appears deactivated
     *    in user listings and application logic.
     * 2. Disables the user in Keycloak via the Admin API, so the user can no longer
     *    authenticate or obtain new JWT tokens.
     *
     * Both steps are necessary: the local flag alone would not prevent login (Keycloak
     * would still issue tokens), and the Keycloak disable alone would not update the
     * local user status for application queries.
     *
     * @param userId the internal UUID of the user to deactivate
     * @throws ResourceNotFoundException if no user exists with the given ID
     */
    @Transactional
    public void deactivateUser(UUID userId) {
        CompanyUser user = companyUserRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("CompanyUser", "id", userId));

        user.setActive(false);
        companyUserRepository.save(user);

        // Also disable in Keycloak so the user cannot authenticate or obtain new tokens
        iamService.disableUser(user.getKeycloakId());
        log.info("Deactivated company user: {}", userId);
    }

    /**
     * Reactivate a previously deactivated company user.
     *
     * Mirrors the deactivation process in reverse:
     * 1. Sets isActive = true in the local database.
     * 2. Re-enables the user in Keycloak, allowing them to authenticate again.
     *
     * @param userId the internal UUID of the user to activate
     * @throws ResourceNotFoundException if no user exists with the given ID
     */
    @Transactional
    public void activateUser(UUID userId) {
        CompanyUser user = companyUserRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("CompanyUser", "id", userId));

        user.setActive(true);
        companyUserRepository.save(user);

        iamService.enableUser(user.getKeycloakId());
        log.info("Activated company user: {}", userId);
    }

    /**
     * Map a CompanyUser entity to a CompanyUserDto for API responses.
     *
     * Flattens the user's Role entities into a Set of role name strings.
     * This avoids exposing internal role details (ID, description, isSystemRole)
     * in the user response; clients that need full role information should use
     * the dedicated /roles endpoint instead.
     *
     * The createdAt field is inherited from the AuditEntity base class.
     */
    private CompanyUserDto mapToDto(CompanyUser user) {
        return CompanyUserDto.builder()
                .id(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .phone(user.getPhone())
                .isActive(user.isActive())
                .roles(user.getRoles().stream()
                        .map(role -> role.getName())
                        .collect(Collectors.toSet()))
                .createdAt(user.getCreatedAt())
                .build();
    }
}
