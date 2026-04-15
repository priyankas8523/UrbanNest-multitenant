package com.multitenant.app.keycloak;

import com.multitenant.app.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RolesResource;
import org.keycloak.representations.idm.RoleRepresentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service responsible for managing Keycloak realm-level roles and user-role assignments.
 *
 * In this multitenant application, roles serve a dual purpose:
 *
 * 1. KEYCLOAK REALM ROLES: Defined at the realm level in Keycloak, these roles are included in
 *    the JWT access token (under the "realm_access.roles" claim). The Spring Boot backend reads
 *    these roles from the JWT to enforce authorization (e.g., @PreAuthorize annotations, security
 *    filters). Realm roles are global across the realm -- all tenants share the same role definitions
 *    (e.g., "ADMIN", "USER", "MANAGER").
 *
 * REALM ROLES vs CLIENT ROLES:
 *    - REALM ROLES (used here): Scoped to the entire Keycloak realm. They appear in the
 *      "realm_access.roles" array in the JWT. Best for application-wide role definitions that
 *      apply uniformly across all tenants.
 *    - CLIENT ROLES: Scoped to a specific Keycloak client (application). They appear under
 *      "resource_access.{client-id}.roles" in the JWT. Useful if multiple applications share
 *      a realm and need independent role sets. Not used in this app because we have a single
 *      application client.
 *
 * 2. TENANT DATABASE ROLES: In addition to Keycloak roles, custom roles are also stored in each
 *    tenant's database schema. This is necessary because tenant admins may need to customize
 *    permissions and role metadata (display names, descriptions, fine-grained permission mappings)
 *    beyond what Keycloak stores. The Keycloak role provides authentication-level access control
 *    (what the JWT says), while the tenant DB role provides application-level business logic
 *    (what the role means in terms of features, menus, permissions within the app). Both must
 *    be kept in sync -- when a role is created, it is created in Keycloak AND in the tenant DB;
 *    when assigned to a user, the assignment happens in both systems.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KeycloakRoleService {

    /**
     * The Keycloak admin client for making Admin REST API calls.
     * See KeycloakUserService for details on authentication and configuration.
     */
    private final Keycloak keycloak;

    /**
     * The Keycloak realm where roles are managed. All tenants share this single realm.
     */
    @Value("${keycloak.admin.realm}")
    private String realm;

    /**
     * Assigns an existing realm role to a user in Keycloak.
     *
     * After this operation, the role will appear in the user's JWT access token under
     * "realm_access.roles" the next time they obtain a token (login or refresh). Existing
     * tokens are NOT retroactively updated -- the user must get a new token to see the role.
     *
     * The Keycloak API requires sending a list of RoleRepresentation objects (not just role names)
     * because internally it needs the role's UUID to create the mapping. That is why we first
     * fetch the full role object via getRealmRole() before passing it to the add() call.
     *
     * This method calls: POST /admin/realms/{realm}/users/{userId}/role-mappings/realm
     * with a JSON body containing the list of roles to assign.
     *
     * @param userId   the Keycloak user UUID to assign the role to
     * @param roleName the name of the realm role (e.g., "ADMIN", "USER")
     * @throws BusinessException if the role does not exist in Keycloak
     */
    public void assignRealmRole(String userId, String roleName) {
        RoleRepresentation role = getRealmRole(roleName);
        keycloak.realm(realm).users().get(userId)
                .roles().realmLevel()
                .add(Collections.singletonList(role));
        log.info("Assigned role '{}' to user '{}'", roleName, userId);
    }

    /**
     * Removes a realm role from a user in Keycloak.
     *
     * After removal, the role will no longer appear in the user's JWT on subsequent token
     * issuance. This is used when demoting a user or changing their access level within a tenant.
     *
     * Like assignRealmRole, this requires the full RoleRepresentation object, not just the name.
     *
     * @param userId   the Keycloak user UUID
     * @param roleName the name of the realm role to remove
     * @throws BusinessException if the role does not exist in Keycloak
     */
    public void removeRealmRole(String userId, String roleName) {
        RoleRepresentation role = getRealmRole(roleName);
        keycloak.realm(realm).users().get(userId)
                .roles().realmLevel()
                .remove(Collections.singletonList(role));
        log.info("Removed role '{}' from user '{}'", roleName, userId);
    }

    /**
     * Creates a new realm-level role in Keycloak if it does not already exist.
     *
     * This is called during application setup or when a tenant admin defines a new custom role.
     * The method is idempotent: if the role already exists, it logs and returns without error.
     * This "check-then-create" pattern uses exception handling for the existence check because
     * Keycloak's Admin API throws a NotFoundException (wrapped in a generic Exception) when
     * fetching a role that does not exist -- there is no dedicated "exists" endpoint.
     *
     * IMPORTANT: Creating a role in Keycloak alone is not sufficient for the application. The
     * corresponding role must also be created in the tenant's database schema (via the tenant
     * role service) so that the application can map the role to specific permissions and features.
     * Keycloak only knows the role name and description; the tenant DB stores the full permission
     * model (which menus to show, which API endpoints to allow, etc.).
     *
     * @param roleName    the unique name for the role (e.g., "MANAGER", "VIEWER")
     * @param description a human-readable description of what the role grants
     */
    public void createRealmRole(String roleName, String description) {
        RolesResource rolesResource = keycloak.realm(realm).roles();

        // Check if role already exists by attempting to fetch it.
        // Keycloak has no "exists" API, so we use a try-catch: if the fetch succeeds,
        // the role exists and we skip creation. If it throws (NotFoundException), we proceed.
        try {
            rolesResource.get(roleName).toRepresentation();
            log.info("Role '{}' already exists in Keycloak", roleName);
            return;
        } catch (Exception ignored) {
            // Role doesn't exist, create it
        }

        RoleRepresentation role = new RoleRepresentation();
        role.setName(roleName);
        role.setDescription(description);
        rolesResource.create(role);
        log.info("Created Keycloak realm role: {}", roleName);
    }

    /**
     * Retrieves all effective realm roles for a given user.
     *
     * "Effective" roles include both directly assigned roles AND roles inherited through
     * composite roles (a Keycloak feature where one role can contain other roles). This gives
     * the complete picture of what the user is authorized to do.
     *
     * Note: This returns ALL realm roles including Keycloak defaults like "default-roles-{realm}",
     * "offline_access", and "uma_authorization". The caller should filter these out if only
     * application-specific roles are needed.
     *
     * @param userId the Keycloak user UUID
     * @return a list of role name strings (e.g., ["ADMIN", "default-roles-myrealm", "offline_access"])
     */
    public List<String> getUserRoles(String userId) {
        return keycloak.realm(realm).users().get(userId)
                .roles().realmLevel().listEffective()
                .stream()
                .map(RoleRepresentation::getName)
                .collect(Collectors.toList());
    }

    /**
     * Fetches the full RoleRepresentation for a realm role by its name.
     *
     * This is a private helper used by assignRealmRole and removeRealmRole. The Keycloak Admin
     * API requires the complete role object (including its internal UUID) for role mapping
     * operations -- passing just the role name is not sufficient. This method bridges that gap
     * by looking up the role and returning the full representation.
     *
     * @param roleName the role name to look up
     * @return the full RoleRepresentation including the role's UUID
     * @throws BusinessException if no role with that name exists in the realm
     */
    private RoleRepresentation getRealmRole(String roleName) {
        try {
            return keycloak.realm(realm).roles().get(roleName).toRepresentation();
        } catch (Exception e) {
            throw new BusinessException("Keycloak role not found: " + roleName);
        }
    }
}
