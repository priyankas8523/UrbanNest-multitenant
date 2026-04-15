package com.multitenant.app.config;

import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Creates the Keycloak Admin Client bean used for server-to-server Keycloak operations.
 *
 * WHY: Our backend needs to programmatically manage Keycloak resources:
 *   - Create users (during company/client registration)
 *   - Create groups (one per tenant)
 *   - Assign/remove roles
 *   - Disable/enable users
 *
 * HOW: The Keycloak Admin Client authenticates using CLIENT_CREDENTIALS grant type,
 * meaning it uses a client_id + client_secret (not a username/password). This is the
 * "multitenant-api" client configured in Keycloak with "Service Account Enabled = true".
 *
 * IMPORTANT: The client_secret must match what's in Keycloak. In production, inject via
 * environment variable KC_ADMIN_SECRET, never hardcode it.
 *
 * This Keycloak bean is injected into:
 *   - KeycloakUserService  (create/delete/enable/disable users)
 *   - KeycloakGroupService (create/delete groups, add/remove users from groups)
 *   - KeycloakRoleService  (assign/remove/create realm roles)
 */
@Configuration
public class KeycloakAdminConfig {

    @Value("${keycloak.admin.server-url}")
    private String serverUrl;       // e.g., http://localhost:8180

    @Value("${keycloak.admin.realm}")
    private String realm;           // "multitenant"

    @Value("${keycloak.admin.client-id}")
    private String clientId;        // "multitenant-api"

    @Value("${keycloak.admin.client-secret}")
    private String clientSecret;    // Secret from Keycloak's client configuration

    @Bean
    public Keycloak keycloakAdmin() {
        return KeycloakBuilder.builder()
                .serverUrl(serverUrl)
                .realm(realm)
                .grantType(OAuth2Constants.CLIENT_CREDENTIALS)  // Machine-to-machine auth (no human login)
                .clientId(clientId)
                .clientSecret(clientSecret)
                .build();
    }
}
