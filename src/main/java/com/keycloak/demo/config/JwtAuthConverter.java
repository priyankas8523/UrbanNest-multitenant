package com.keycloak.demo.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Converts a Keycloak JWT into a Spring Security Authentication object with proper roles.
 *
 * WHY THIS IS NEEDED:
 * Spring's default JWT converter looks for roles in a "scope" or "scp" claim, but Keycloak
 * puts roles in a completely different structure:
 *
 * Keycloak JWT structure (decoded payload):
 * {
 *   "sub": "550e8400-...",                    // User's Keycloak UUID
 *   "preferred_username": "john@acme.com",    // Used as the principal name
 *   "tenant_id": "acme-corp",                 // Custom claim: which tenant this user belongs to
 *   "portal_type": "company",                 // Custom claim: which portal they logged in from
 *   "realm_access": {                         // Realm-level roles (ADMIN, ROLE1, CLIENT, etc.)
 *     "roles": ["ADMIN", "ROLE1"]
 *   },
 *   "resource_access": {                      // Client-specific roles (less commonly used)
 *     "multitenant-api": {
 *       "roles": ["manage-account"]
 *     }
 *   }
 * }
 *
 * This converter extracts roles from BOTH realm_access and resource_access,
 * prefixes them with "ROLE_" (Spring Security convention), and creates a
 * JwtAuthenticationToken that @PreAuthorize can check against.
 *
 * Example: JWT with realm_access.roles = ["ADMIN"] -> GrantedAuthority("ROLE_ADMIN")
 *          -> @PreAuthorize("hasRole('ADMIN')") passes (Spring auto-adds ROLE_ prefix when checking)
 */
@Component
public class JwtAuthConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    @Value("${keycloak.admin.client-id}")
    private String clientId;  // "multitenant-api" - used to look up client-specific roles

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        // Merge realm-level and client-level roles into one set of authorities
        Collection<GrantedAuthority> authorities = Stream.concat(
                extractRealmRoles(jwt).stream(),
                extractResourceRoles(jwt).stream()
        ).collect(Collectors.toSet());

        // The 3rd parameter sets the "name" of the authenticated principal
        // (shown in logs and available via authentication.getName())
        return new JwtAuthenticationToken(jwt, authorities, jwt.getClaimAsString("preferred_username"));
    }

    /**
     * Extracts roles from jwt.realm_access.roles[]
     * These are the main roles: SUPER_ADMIN, ADMIN, ROLE1, ROLE2, CLIENT
     *
     * Keycloak JWT snippet:
     *   "realm_access": { "roles": ["ADMIN", "ROLE1", "default-roles-urbannest"] }
     */
    private Collection<GrantedAuthority> extractRealmRoles(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaim("realm_access");
        if (realmAccess == null) {
            return Collections.emptySet();
        }

        @SuppressWarnings("unchecked")
        Collection<String> roles = (Collection<String>) realmAccess.get("roles");
        if (roles == null) {
            return Collections.emptySet();
        }

        // Prefix with "ROLE_" because Spring Security's hasRole('ADMIN') checks for "ROLE_ADMIN"
        return roles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .collect(Collectors.toSet());
    }

    /**
     * Extracts roles from jwt.resource_access.{clientId}.roles[]
     * These are client-specific roles assigned in Keycloak's client configuration.
     * Less commonly used in this app, but included for completeness.
     */
    private Collection<GrantedAuthority> extractResourceRoles(Jwt jwt) {
        Map<String, Object> resourceAccess = jwt.getClaim("resource_access");
        if (resourceAccess == null) {
            return Collections.emptySet();
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> clientAccess = (Map<String, Object>) resourceAccess.get(clientId);
        if (clientAccess == null) {
            return Collections.emptySet();
        }

        @SuppressWarnings("unchecked")
        Collection<String> roles = (Collection<String>) clientAccess.get("roles");
        if (roles == null) {
            return Collections.emptySet();
        }

        return roles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .collect(Collectors.toSet());
    }
}
