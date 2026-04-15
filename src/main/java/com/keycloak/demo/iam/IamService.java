package com.multitenant.app.iam;

import com.multitenant.app.module.auth.dto.LoginResponse;

import java.util.List;
import java.util.Map;

/**
 * IAM (Identity & Access Management) Service - single facade for ALL Keycloak operations.
 *
 * PATTERN: Consolidates the 4 separate Keycloak*Service classes (User, Group, Role, Auth)
 * into one unified interface, following the OLA project's IamService pattern.
 *
 * WHY ONE SERVICE:
 * - Single point of contact for identity operations (easier to mock, test, swap providers)
 * - If we ever replace Keycloak with Auth0/Cognito, only this interface's impl changes
 * - Cleaner dependency injection (inject IamService instead of 4 separate services)
 *
 * OPERATIONS GROUPED:
 *   User Management  -> createUser, deleteUser, enableUser, disableUser, updateUserAttributes
 *   Group Management -> createGroup, deleteGroup, addUserToGroup, getGroupIdByName
 *   Role Management  -> assignRole, removeRole, createRole, getUserRoles
 *   Authentication   -> login, refreshToken, logout
 */
public interface IamService {

    // ==================== USER MANAGEMENT ====================

    /** Create user in Keycloak with custom attributes (tenant_id, portal_type). Returns Keycloak user ID. */
    String createUser(String email, String password, String firstName, String lastName, Map<String, String> attributes);

    /** Permanently delete a user from Keycloak. Used in saga compensation. */
    void deleteUser(String keycloakId);

    /** Enable a previously disabled user. Allows login again. */
    void enableUser(String keycloakId);

    /** Disable user in Keycloak. Blocks all login attempts. */
    void disableUser(String keycloakId);

    /** Update custom attributes on a user (e.g., tenant_id, portal_type). */
    void updateUserAttributes(String keycloakId, Map<String, List<String>> attributes);

    // ==================== GROUP MANAGEMENT ====================

    /** Create a Keycloak group for a tenant (group name = "tenant_{slug}"). Returns group ID. */
    String createGroup(String tenantId);

    /** Delete a Keycloak group. Used in saga compensation. */
    void deleteGroup(String groupId);

    /** Add a user to a tenant's group. */
    void addUserToGroup(String userId, String groupId);

    /** Remove a user from a tenant's group. */
    void removeUserFromGroup(String userId, String groupId);

    /** Look up a group's ID by its name. */
    String getGroupIdByName(String groupName);

    // ==================== ROLE MANAGEMENT ====================

    /** Assign a realm-level role to a user. Role appears in JWT on next login. */
    void assignRealmRole(String userId, String roleName);

    /** Remove a realm-level role from a user. */
    void removeRealmRole(String userId, String roleName);

    /** Create a new realm-level role. Idempotent - skips if role exists. */
    void createRealmRole(String roleName, String description);

    /** Get all effective realm roles for a user. */
    List<String> getUserRoles(String userId);

    // ==================== AUTHENTICATION ====================

    /** Authenticate user with email/password. Returns access + refresh tokens. */
    LoginResponse login(String username, String password, String clientId, String clientSecret);

    /** Exchange refresh token for new token pair. Old refresh token is invalidated. */
    LoginResponse refreshToken(String refreshToken, String clientId, String clientSecret);

    /** Invalidate refresh token and end Keycloak session. */
    void logout(String refreshToken, String clientId, String clientSecret);
}
