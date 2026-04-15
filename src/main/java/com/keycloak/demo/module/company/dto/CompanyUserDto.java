package com.keycloak.demo.module.company.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Response DTO for company user data returned to the client.
 *
 * This DTO is used for both the /profile endpoint (current user's own data)
 * and the /users endpoints (admin listing all company users). It flattens
 * the entity's Role relationship into a simple Set of role name strings,
 * avoiding exposing internal role IDs or the full Role entity structure
 * to the API consumer.
 *
 * The mapping from {@link com.multitenant.app.module.company.entity.CompanyUser}
 * to this DTO is done in CompanyService.mapToDto(), which extracts role names
 * from the user's associated Role entities.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompanyUserDto {

    /** Internal UUID of the company user within the tenant's schema. */
    private UUID id;

    /** The user's email address. */
    private String email;

    /** The user's first name. */
    private String firstName;

    /** The user's last name. */
    private String lastName;

    /** The user's phone number. */
    private String phone;

    /**
     * Whether the user is currently active.
     * False means the user has been soft-deleted (deactivated) and cannot log in.
     */
    private boolean isActive;

    /**
     * Set of role names assigned to this user (e.g., {"ADMIN", "FINANCE_MANAGER"}).
     * This is a flattened representation -- the full Role entity (with ID, description,
     * isSystemRole) is not exposed here. Use the /roles endpoint for full role details.
     */
    private Set<String> roles;

    /** Timestamp of when this user record was created (inherited from AuditEntity). */
    private Instant createdAt;
}
