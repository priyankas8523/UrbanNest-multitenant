package com.multitenant.app.iam;

import com.multitenant.app.keycloak.KeycloakAuthService;
import com.multitenant.app.keycloak.KeycloakGroupService;
import com.multitenant.app.keycloak.KeycloakRoleService;
import com.multitenant.app.keycloak.KeycloakUserService;
import com.multitenant.app.module.auth.dto.LoginResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * IamServiceImpl - Unified facade that delegates to the 4 Keycloak service classes.
 *
 * ARCHITECTURE (like OLA project's IamServiceImpl):
 * Instead of controllers/services injecting 4 separate Keycloak classes, they inject
 * this single IamService. This class simply delegates to the correct Keycloak service.
 *
 * BENEFITS:
 * - One import, one dependency instead of four
 * - Easy to swap Keycloak for another provider (just write a new IamServiceImpl)
 * - All IAM operations discoverable in one interface
 * - Easier to add cross-cutting concerns (logging, metrics, circuit-breaking)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IamServiceImpl implements IamService {

    // Delegates - the existing Keycloak services still do the actual work
    private final KeycloakUserService userService;
    private final KeycloakGroupService groupService;
    private final KeycloakRoleService roleService;
    private final KeycloakAuthService authService;

    // ==================== USER MANAGEMENT ====================

    @Override
    public String createUser(String email, String password, String firstName,
                             String lastName, Map<String, String> attributes) {
        log.info("IAM: Creating user '{}'", email);
        return userService.createUser(email, password, firstName, lastName, attributes);
    }

    @Override
    public void deleteUser(String keycloakId) {
        log.info("IAM: Deleting user '{}'", keycloakId);
        userService.deleteUser(keycloakId);
    }

    @Override
    public void enableUser(String keycloakId) {
        log.info("IAM: Enabling user '{}'", keycloakId);
        userService.enableUser(keycloakId);
    }

    @Override
    public void disableUser(String keycloakId) {
        log.info("IAM: Disabling user '{}'", keycloakId);
        userService.disableUser(keycloakId);
    }

    @Override
    public void updateUserAttributes(String keycloakId, Map<String, List<String>> attributes) {
        userService.updateUserAttributes(keycloakId, attributes);
    }

    // ==================== GROUP MANAGEMENT ====================

    @Override
    public String createGroup(String tenantId) {
        log.info("IAM: Creating group for tenant '{}'", tenantId);
        return groupService.createGroup(tenantId);
    }

    @Override
    public void deleteGroup(String groupId) {
        log.info("IAM: Deleting group '{}'", groupId);
        groupService.deleteGroup(groupId);
    }

    @Override
    public void addUserToGroup(String userId, String groupId) {
        groupService.addUserToGroup(userId, groupId);
    }

    @Override
    public void removeUserFromGroup(String userId, String groupId) {
        groupService.removeUserFromGroup(userId, groupId);
    }

    @Override
    public String getGroupIdByName(String groupName) {
        return groupService.getGroupIdByName(groupName);
    }

    // ==================== ROLE MANAGEMENT ====================

    @Override
    public void assignRealmRole(String userId, String roleName) {
        log.info("IAM: Assigning role '{}' to user '{}'", roleName, userId);
        roleService.assignRealmRole(userId, roleName);
    }

    @Override
    public void removeRealmRole(String userId, String roleName) {
        log.info("IAM: Removing role '{}' from user '{}'", roleName, userId);
        roleService.removeRealmRole(userId, roleName);
    }

    @Override
    public void createRealmRole(String roleName, String description) {
        roleService.createRealmRole(roleName, description);
    }

    @Override
    public List<String> getUserRoles(String userId) {
        return roleService.getUserRoles(userId);
    }

    // ==================== AUTHENTICATION ====================

    @Override
    public LoginResponse login(String username, String password, String clientId, String clientSecret) {
        log.info("IAM: Login attempt for '{}'", username);
        return authService.login(username, password, clientId, clientSecret);
    }

    @Override
    public LoginResponse refreshToken(String refreshToken, String clientId, String clientSecret) {
        return authService.refreshToken(refreshToken, clientId, clientSecret);
    }

    @Override
    public void logout(String refreshToken, String clientId, String clientSecret) {
        log.info("IAM: Logout requested");
        authService.logout(refreshToken, clientId, clientSecret);
    }
}
