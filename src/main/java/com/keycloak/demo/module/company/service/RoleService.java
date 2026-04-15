package com.keycloak.demo.module.company.service;

import com.keycloak.demo.common.exception.BusinessException;
import com.keycloak.demo.common.exception.ResourceNotFoundException;
import com.keycloak.demo.iam.IamService;
import com.keycloak.demo.module.company.dto.AddRoleRequest;
import com.keycloak.demo.module.company.dto.AssignRoleRequest;
import com.keycloak.demo.module.company.dto.RoleDto;
import com.keycloak.demo.module.company.entity.CompanyUser;
import com.keycloak.demo.module.company.entity.Role;
import com.keycloak.demo.module.company.repository.CompanyUserRepository;
import com.keycloak.demo.module.company.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service layer for role management within the current tenant.
 *
 * Handles role CRUD operations and role-to-user assignment/removal.
 *
 * DUAL-WRITE PATTERN:
 * Every role mutation (create, assign, remove) is written to BOTH the local database
 * AND Keycloak. This is necessary because:
 *
 * 1. Keycloak needs the role so it can include it in the user's JWT token claims.
 *    The Spring Security @PreAuthorize checks (e.g., "hasRole('ADMIN')") rely on
 *    roles being present in the JWT, which Keycloak populates from its own role store.
 *
 * 2. The local database needs the role for application-level queries (e.g., listing
 *    roles in a tenant, checking role assignments in business logic, displaying
 *    role information in the UI).
 *
 * The Keycloak write is performed first in create/assign/remove operations. If the
 * Keycloak call fails, the local DB transaction rolls back (since the method is
 * @Transactional), keeping both systems consistent. If the Keycloak call succeeds
 * but the DB write fails, a manual reconciliation may be needed -- this is a known
 * tradeoff of the dual-write pattern without distributed transactions.
 *
 * Dependencies:
 * - {@link RoleRepository}: tenant-scoped database access for roles.
 * - {@link CompanyUserRepository}: tenant-scoped database access for company users.
 * - {@link KeycloakRoleService}: Keycloak Admin API integration for role operations.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoleService {

    private final RoleRepository roleRepository;
    private final CompanyUserRepository companyUserRepository;
    private final IamService iamService;  // Unified IAM facade (replaces KeycloakRoleService)

    /**
     * List all roles in the current tenant's schema.
     *
     * Returns both system roles (ADMIN, ROLE1, ROLE2) and custom roles.
     * The isSystemRole flag in each RoleDto lets the caller distinguish between them.
     *
     * @return list of all roles as DTOs
     */
    @Transactional(readOnly = true)
    public List<RoleDto> listRoles() {
        return roleRepository.findAll().stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    /**
     * Create a new custom role in both Keycloak and the local database.
     *
     * Flow:
     * 1. Check for duplicate role name in the local DB (prevents conflicts).
     * 2. Create the role in Keycloak first (as the realm role).
     * 3. Create the role in the local DB with isSystemRole = false.
     *
     * Keycloak is written first because it is the harder operation to roll back.
     * If the Keycloak call succeeds but the DB save fails, the @Transactional
     * annotation ensures the DB change rolls back, though the Keycloak role
     * would remain (requiring manual cleanup in edge cases).
     *
     * @param request the role creation request containing name and optional description
     * @return the newly created role as a DTO
     * @throws BusinessException if a role with the same name already exists
     */
    @Transactional
    public RoleDto createRole(AddRoleRequest request) {
        if (roleRepository.existsByName(request.getName())) {
            throw new BusinessException("Role already exists: " + request.getName());
        }

        // Create in Keycloak first so the role is available for JWT token claims.
        // If this fails, the exception propagates and no local DB record is created.
        iamService.createRealmRole(request.getName(), request.getDescription());

        // Create in local DB with isSystemRole = false (custom roles are never system roles)
        Role role = Role.builder()
                .name(request.getName())
                .description(request.getDescription())
                .isSystemRole(false)
                .build();
        role = roleRepository.save(role);

        log.info("Created custom role: {}", request.getName());
        return mapToDto(role);
    }

    /**
     * Assign an existing role to a company user.
     *
     * Performs a dual-write:
     * 1. Assigns the realm role to the user in Keycloak, so the role appears in
     *    the user's JWT on their next authentication.
     * 2. Adds the role to the user's role set in the local DB (company_user_roles
     *    join table), so the assignment is reflected in application queries.
     *
     * Validates that the user does not already have the role to prevent duplicates
     * and provide a clear error message.
     *
     * @param request contains the userId and roleId to assign
     * @throws ResourceNotFoundException if the user or role does not exist
     * @throws BusinessException if the user already has the specified role
     */
    @Transactional
    public void assignRoleToUser(AssignRoleRequest request) {
        CompanyUser user = companyUserRepository.findById(request.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("CompanyUser", "id", request.getUserId()));

        Role role = roleRepository.findById(request.getRoleId())
                .orElseThrow(() -> new ResourceNotFoundException("Role", "id", request.getRoleId()));

        if (user.getRoles().contains(role)) {
            throw new BusinessException("User already has role: " + role.getName());
        }

        // Assign in Keycloak first so the role is included in the user's next JWT
        iamService.assignRealmRole(user.getKeycloakId(), role.getName());

        // Assign in local DB by adding to the many-to-many relationship set.
        // JPA will insert a row into the company_user_roles join table on save.
        user.getRoles().add(role);
        companyUserRepository.save(user);

        log.info("Assigned role '{}' to user '{}'", role.getName(), user.getEmail());
    }

    /**
     * Remove a role from a company user.
     *
     * Mirrors the assign operation: removes the role from both Keycloak and the
     * local database. After removal, the role will no longer appear in the user's
     * JWT on their next authentication, and the join table row is deleted.
     *
     * Note: This does not delete the role itself, only the user-role association.
     *
     * @param roleId the UUID of the role to remove
     * @param userId the UUID of the user to remove the role from
     * @throws ResourceNotFoundException if the user or role does not exist
     * @throws BusinessException if the user does not currently have the specified role
     */
    @Transactional
    public void removeRoleFromUser(UUID roleId, UUID userId) {
        CompanyUser user = companyUserRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("CompanyUser", "id", userId));

        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException("Role", "id", roleId));

        if (!user.getRoles().contains(role)) {
            throw new BusinessException("User does not have role: " + role.getName());
        }

        // Remove from Keycloak first so the role is excluded from the user's next JWT
        iamService.removeRealmRole(user.getKeycloakId(), role.getName());

        // Remove from local DB by removing from the many-to-many relationship set.
        // JPA will delete the corresponding row from the company_user_roles join table.
        user.getRoles().remove(role);
        companyUserRepository.save(user);

        log.info("Removed role '{}' from user '{}'", role.getName(), user.getEmail());
    }

    /**
     * Map a Role entity to a RoleDto for API responses.
     */
    private RoleDto mapToDto(Role role) {
        return RoleDto.builder()
                .id(role.getId())
                .name(role.getName())
                .description(role.getDescription())
                .isSystemRole(role.isSystemRole())
                .build();
    }
}
