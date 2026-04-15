package com.keycloak.demo.keycloak;

import com.keycloak.demo.common.exception.BusinessException;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Service responsible for managing user lifecycle in Keycloak for the multitenant application.
 *
 * In this architecture, Keycloak is the single source of truth for user identity and authentication.
 * Every user in the system (regardless of which tenant they belong to) is stored as a Keycloak user
 * within a single shared realm. Tenant isolation is achieved NOT by separate realms, but by custom
 * user attributes (tenant_id, portal_type) that are embedded into JWT tokens via Keycloak protocol
 * mappers. The Spring Boot backend then reads these JWT claims to resolve the current tenant and
 * route requests to the correct tenant schema in the database.
 *
 * This service uses the Keycloak Admin Client (org.keycloak:keycloak-admin-client), which is a
 * Java SDK that wraps Keycloak's Admin REST API. The injected {@code Keycloak} instance is
 * pre-configured with admin credentials (typically a service account) and handles token management
 * internally -- it automatically obtains and refreshes its own admin access token behind the scenes.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KeycloakUserService {

    /**
     * The Keycloak admin client instance, injected from the KeycloakConfig bean.
     * This client authenticates as an admin service account and provides programmatic
     * access to all Keycloak Admin REST API operations (user CRUD, role assignment, etc.).
     */
    private final Keycloak keycloak;

    /**
     * The Keycloak realm in which all tenant users are managed.
     * In this multitenant setup, a single realm is shared across all tenants.
     * Tenant separation happens at the attribute/group level, not the realm level.
     */
    @Value("${keycloak.admin.realm}")
    private String realm;

    /**
     * Creates a new user in Keycloak with tenant-specific custom attributes.
     *
     * This method is called during tenant user onboarding (e.g., when a company admin invites
     * a new employee, or during initial tenant provisioning for the first admin user).
     *
     * Key design decisions:
     *
     * 1. EMAIL AS USERNAME: We set both email and username to the same value. This simplifies
     *    login (users authenticate with their email) and guarantees uniqueness since Keycloak
     *    enforces unique usernames per realm.
     *
     * 2. CUSTOM ATTRIBUTES (tenant_id, portal_type): These are the cornerstone of our multitenant
     *    identity model. Keycloak stores them as key-value pairs on the user record. A Keycloak
     *    protocol mapper (configured in the Keycloak client settings) copies these attributes into
     *    the JWT access token as custom claims. When the Spring Boot backend receives a request,
     *    the security filter extracts "tenant_id" from the JWT to resolve which tenant database
     *    schema to use, and "portal_type" to determine access level (e.g., admin portal vs user portal).
     *    Keycloak attributes are always stored as List<String> even for single values -- this is
     *    a Keycloak API convention, hence the Collections.singletonList() wrapping.
     *
     * 3. EMAIL_VERIFIED = TRUE: We set emailVerified to true because in this system, user accounts
     *    are created by administrators (not self-registered). The admin has already verified the
     *    employee's identity through their own process. Setting this to true prevents Keycloak
     *    from requiring an email verification step, which would add unnecessary friction to
     *    admin-provisioned accounts. If self-registration were supported, this should be false.
     *
     * 4. PASSWORD CREDENTIAL: The password is set as non-temporary, meaning the user will not be
     *    forced to change it on first login. CredentialRepresentation.PASSWORD tells Keycloak
     *    to hash and store this as the user's login password (Keycloak handles hashing internally).
     *
     * 5. EXTRACTING USER ID FROM LOCATION HEADER: Keycloak's Admin REST API follows the HTTP/REST
     *    convention where a successful resource creation returns HTTP 201 Created with a Location
     *    header pointing to the new resource. The header value looks like:
     *    "http://keycloak-host/admin/realms/{realm}/users/{user-id}"
     *    Since Keycloak does NOT return the user ID in the response body for create operations,
     *    we must parse it from this Location header by extracting everything after the last '/'.
     *    This user ID (a UUID) is then stored in our application database to link the local user
     *    record to the Keycloak identity, enabling future operations like role assignment or deletion.
     *
     * @param email      the user's email, also used as the Keycloak username
     * @param password   the initial password for the user (Keycloak will hash it)
     * @param firstName  the user's first name
     * @param lastName   the user's last name
     * @param attributes custom attributes map, expected to contain "tenant_id" and "portal_type"
     * @return the Keycloak-generated user ID (UUID string), used to reference this user in future API calls
     * @throws BusinessException if a user with the same email already exists or if creation fails
     */
    public String createUser(String email, String password, String firstName,
                             String lastName, Map<String, String> attributes) {
        UsersResource usersResource = keycloak.realm(realm).users();

        // Check if user already exists
        // The second parameter (true) means exact match -- prevents partial email matches
        // (e.g., searching "john@acme.com" won't match "john@acme.com.au")
        List<UserRepresentation> existingUsers = usersResource.searchByEmail(email, true);
        if (!existingUsers.isEmpty()) {
            throw new BusinessException("User with email " + email + " already exists in Keycloak");
        }

        UserRepresentation user = new UserRepresentation();
        user.setEmail(email);
        user.setUsername(email);
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setEnabled(true);
        user.setEmailVerified(true);

        // Set custom attributes (tenant_id, portal_type)
        // These attributes are mapped to JWT claims via Keycloak protocol mappers,
        // allowing the backend to identify the tenant and portal context from the token alone.
        Map<String, List<String>> userAttributes = new java.util.HashMap<>();
        attributes.forEach((key, value) -> userAttributes.put(key, Collections.singletonList(value)));
        user.setAttributes(userAttributes);

        // Set password credential -- Keycloak will hash and store it securely.
        // setTemporary(false) means the user will NOT be prompted to change password on first login.
        CredentialRepresentation credential = new CredentialRepresentation();
        credential.setType(CredentialRepresentation.PASSWORD);
        credential.setValue(password);
        credential.setTemporary(false);
        user.setCredentials(Collections.singletonList(credential));

        // Send the create request to Keycloak Admin REST API.
        // The response is wrapped in a try-with-resources because the JAX-RS Response
        // holds an underlying HTTP connection that must be closed to prevent resource leaks.
        try (Response response = usersResource.create(user)) {
            if (response.getStatus() == 201) {
                // Keycloak returns the new user's URL in the Location header (REST convention).
                // The user ID (UUID) is the last path segment of that URL.
                // This is the only way to obtain the ID from a create operation -- the response body is empty.
                String locationHeader = response.getHeaderString("Location");
                String userId = locationHeader.substring(locationHeader.lastIndexOf('/') + 1);
                log.info("Created Keycloak user '{}' with ID: {}", email, userId);

                // Keycloak 24 does not reliably persist attributes set on the UserRepresentation
                // during the initial create call. A separate update call is required to guarantee
                // the attributes (tenant_id, portal_type) are saved and appear in JWT claims.
                if (!userAttributes.isEmpty()) {
                    UserRepresentation created = usersResource.get(userId).toRepresentation();
                    created.setAttributes(userAttributes);
                    usersResource.get(userId).update(created);
                    log.info("Set attributes {} on user '{}'", userAttributes.keySet(), email);
                }

                return userId;
            } else {
                // Read the error body for diagnostic details (e.g., "User exists with same username")
                String body = response.readEntity(String.class);
                throw new BusinessException("Failed to create Keycloak user: " + body);
            }
        }
    }

    /**
     * Retrieves a user's full representation from Keycloak by their Keycloak user ID (UUID).
     *
     * The returned UserRepresentation includes all user details: email, name, attributes,
     * enabled status, etc. This is used internally by other methods in this service (e.g.,
     * enableUser, disableUser, updateUserAttributes) that need to fetch-then-modify the user,
     * since Keycloak's update API requires sending the full user object.
     *
     * @param keycloakId the Keycloak user UUID
     * @return the full user representation from Keycloak
     */
    public UserRepresentation getUserById(String keycloakId) {
        return keycloak.realm(realm).users().get(keycloakId).toRepresentation();
    }

    /**
     * Permanently deletes a user from Keycloak.
     *
     * This is called when removing a user from the system entirely (e.g., during tenant
     * deprovisioning or user account deletion). The deletion is irreversible -- the user
     * will no longer be able to authenticate. Any active sessions/tokens for this user
     * are invalidated by Keycloak.
     *
     * Errors are caught and logged rather than propagated because this is typically called
     * as part of a cleanup flow (e.g., tenant deletion) where a failure to delete one user
     * should not block the deletion of remaining users. The caller can check logs for issues.
     *
     * @param keycloakId the Keycloak user UUID to delete
     */
    public void deleteUser(String keycloakId) {
        try {
            keycloak.realm(realm).users().delete(keycloakId);
            log.info("Deleted Keycloak user: {}", keycloakId);
        } catch (Exception e) {
            log.error("Failed to delete Keycloak user: {}", keycloakId, e);
        }
    }

    /**
     * Updates custom attributes on an existing Keycloak user.
     *
     * This performs a merge: existing attributes are preserved, and the provided attributes
     * are added or overwritten. This is useful for updating tenant-specific metadata without
     * losing other attributes that may have been set previously.
     *
     * A common use case is updating the portal_type when a user's access level changes
     * (e.g., promoted from regular user to admin). Since these attributes flow into JWT
     * claims, the change takes effect when the user next obtains a new token.
     *
     * @param keycloakId the Keycloak user UUID
     * @param attributes map of attribute names to their values (as List<String>, per Keycloak convention)
     */
    public void updateUserAttributes(String keycloakId, Map<String, List<String>> attributes) {
        UserRepresentation user = getUserById(keycloakId);
        Map<String, List<String>> existingAttributes = user.getAttributes();
        if (existingAttributes == null) {
            existingAttributes = new java.util.HashMap<>();
        }
        existingAttributes.putAll(attributes);
        user.setAttributes(existingAttributes);
        keycloak.realm(realm).users().get(keycloakId).update(user);
    }

    /**
     * Enables a previously disabled Keycloak user, allowing them to authenticate again.
     *
     * When a user is enabled, they can log in and obtain new tokens. This is typically called
     * when reactivating a suspended user account (e.g., after resolving a billing issue or
     * reversing an administrative suspension).
     *
     * @param keycloakId the Keycloak user UUID to enable
     */
    public void enableUser(String keycloakId) {
        UserRepresentation user = getUserById(keycloakId);
        user.setEnabled(true);
        keycloak.realm(realm).users().get(keycloakId).update(user);
    }

    /**
     * Disables a Keycloak user, preventing them from authenticating.
     *
     * A disabled user cannot obtain new tokens, but existing tokens remain valid until they
     * expire (unless explicitly revoked). This is preferred over deletion when the suspension
     * is temporary (e.g., non-payment, security investigation) because the user record and
     * all its attributes, roles, and group memberships are preserved and can be restored
     * by calling enableUser().
     *
     * @param keycloakId the Keycloak user UUID to disable
     */
    public void disableUser(String keycloakId) {
        UserRepresentation user = getUserById(keycloakId);
        user.setEnabled(false);
        keycloak.realm(realm).users().get(keycloakId).update(user);
    }
}
