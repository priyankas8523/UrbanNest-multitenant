package com.multitenant.app.keycloak;

import com.multitenant.app.common.constants.AppConstants;
import com.multitenant.app.common.exception.BusinessException;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.GroupRepresentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service responsible for managing Keycloak groups that represent tenants.
 *
 * In this multitenant architecture, each tenant (company) gets its own Keycloak group.
 * Groups serve as a logical container for all users belonging to a specific tenant.
 * While the primary tenant isolation mechanism is the custom user attribute "tenant_id"
 * (which flows into JWT claims), groups provide additional organizational benefits:
 *
 * 1. VISIBILITY: Admins can see all users in a tenant at a glance in the Keycloak admin console
 *    by browsing the tenant's group membership.
 *
 * 2. BULK OPERATIONS: Keycloak allows assigning roles or policies to groups, which automatically
 *    apply to all group members. This could be used for tenant-wide role assignments.
 *
 * 3. GROUP-BASED POLICIES: Keycloak authorization services can define policies based on group
 *    membership, enabling tenant-scoped access control rules if needed in the future.
 *
 * 4. AUDIT & COMPLIANCE: Group membership provides a clear record of which users belong to
 *    which tenant, independent of the user attribute -- useful for cross-referencing.
 *
 * NAMING CONVENTION: Groups follow the pattern "tenant_{slug}" (e.g., "tenant_acme-corp"),
 * using the same prefix as the database schema names. This consistency makes it easy to
 * correlate a Keycloak group with its corresponding database schema and tenant record.
 * The prefix is defined in AppConstants.TENANT_SCHEMA_PREFIX ("tenant_").
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KeycloakGroupService {

    /**
     * The Keycloak admin client instance for making Admin REST API calls.
     * See KeycloakUserService for details on how this client is configured and authenticated.
     */
    private final Keycloak keycloak;

    /**
     * The shared Keycloak realm where all tenant groups are created.
     * All tenants share one realm; groups within that realm provide tenant-level organization.
     */
    @Value("${keycloak.admin.realm}")
    private String realm;

    /**
     * Creates a new Keycloak group for a tenant during the tenant provisioning process.
     *
     * This is called as part of the tenant onboarding workflow (typically in TenantProvisioningService)
     * alongside creating the tenant's database schema and initial admin user. The group name follows
     * the convention "tenant_{tenantId}" (e.g., "tenant_acme-corp"), matching the database schema name.
     *
     * Similar to user creation, Keycloak returns the new group's ID only via the Location header
     * of the HTTP 201 response (not in the body). The group ID (UUID) is extracted by parsing
     * the last path segment of the Location URL. This ID is stored in the application's master
     * database (Tenant table) so we can reference it later for adding/removing users.
     *
     * @param tenantId the tenant's unique slug identifier (e.g., "acme-corp")
     * @return the Keycloak-generated group ID (UUID string) for storing in the application database
     * @throws BusinessException if group creation fails (e.g., duplicate name, Keycloak unavailable)
     */
    public String createGroup(String tenantId) {
        // Build the group name using the shared prefix, e.g., "tenant_acme-corp".
        // This matches the naming convention used for database schemas, ensuring consistency
        // across the entire multitenant infrastructure.
        String groupName = AppConstants.TENANT_SCHEMA_PREFIX + tenantId;

        GroupRepresentation group = new GroupRepresentation();
        group.setName(groupName);

        // Send the create request. The JAX-RS Response is closed via try-with-resources
        // to release the underlying HTTP connection and prevent resource leaks.
        try (Response response = keycloak.realm(realm).groups().add(group)) {
            if (response.getStatus() == 201) {
                // Extract the group UUID from the Location header, same pattern as user creation.
                // Location header format: "http://keycloak-host/admin/realms/{realm}/groups/{group-id}"
                String locationHeader = response.getHeaderString("Location");
                String groupId = locationHeader.substring(locationHeader.lastIndexOf('/') + 1);
                log.info("Created Keycloak group '{}' with ID: {}", groupName, groupId);
                return groupId;
            } else {
                throw new BusinessException("Failed to create Keycloak group: " + response.getStatusInfo());
            }
        }
    }

    /**
     * Adds a user to a tenant's Keycloak group.
     *
     * This is called after creating a user in Keycloak (via KeycloakUserService.createUser)
     * to associate them with their tenant's group. The user already has the tenant_id attribute
     * on their profile, so group membership is a supplementary association -- it provides the
     * organizational benefits described in the class-level documentation above.
     *
     * Under the hood, this calls PUT /admin/realms/{realm}/users/{userId}/groups/{groupId}
     * which is an idempotent operation -- calling it multiple times for the same user/group
     * combination has no adverse effect.
     *
     * @param userId  the Keycloak user UUID
     * @param groupId the Keycloak group UUID (obtained from createGroup or getGroupIdByName)
     */
    public void addUserToGroup(String userId, String groupId) {
        keycloak.realm(realm).users().get(userId).joinGroup(groupId);
        log.info("Added user '{}' to group '{}'", userId, groupId);
    }

    /**
     * Removes a user from a tenant's Keycloak group.
     *
     * This is used when a user is being removed from a tenant (e.g., employee leaves the company)
     * or during user deletion cleanup. Removing from the group does not delete the user from
     * Keycloak -- it only severs the group membership association.
     *
     * @param userId  the Keycloak user UUID
     * @param groupId the Keycloak group UUID
     */
    public void removeUserFromGroup(String userId, String groupId) {
        keycloak.realm(realm).users().get(userId).leaveGroup(groupId);
        log.info("Removed user '{}' from group '{}'", userId, groupId);
    }

    /**
     * Looks up a Keycloak group's UUID by its display name.
     *
     * This is a convenience method for situations where we have the tenant slug but not the
     * stored group ID. The search parameters (groupName, 0, 1) mean: search for groups matching
     * the name, starting at offset 0, returning at most 1 result. Keycloak's group search is
     * a prefix/contains match, so the caller should pass the full exact name (e.g., "tenant_acme-corp")
     * and verify the result is correct if ambiguity is possible.
     *
     * @param groupName the full group name to search for (e.g., "tenant_acme-corp")
     * @return the Keycloak group UUID
     * @throws BusinessException if no group is found with the given name
     */
    public String getGroupIdByName(String groupName) {
        List<GroupRepresentation> groups = keycloak.realm(realm).groups()
                .groups(groupName, 0, 1);
        if (groups.isEmpty()) {
            throw new BusinessException("Keycloak group not found: " + groupName);
        }
        return groups.get(0).getId();
    }

    /**
     * Permanently deletes a Keycloak group.
     *
     * This is called during tenant deprovisioning (when a tenant/company is removed from the system).
     * Deleting a group does NOT delete its member users -- it only removes the group entity and
     * the membership associations. Users must be deleted separately via KeycloakUserService.
     *
     * Errors are caught and logged (not propagated) because this is typically part of a multi-step
     * cleanup process where failure of one step should not block subsequent cleanup operations.
     *
     * @param groupId the Keycloak group UUID to delete
     */
    public void deleteGroup(String groupId) {
        try {
            keycloak.realm(realm).groups().group(groupId).remove();
            log.info("Deleted Keycloak group: {}", groupId);
        } catch (Exception e) {
            log.error("Failed to delete Keycloak group: {}", groupId, e);
        }
    }
}
