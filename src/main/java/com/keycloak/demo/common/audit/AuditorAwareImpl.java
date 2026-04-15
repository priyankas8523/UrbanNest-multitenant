package com.multitenant.app.common.audit;

import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Tells Spring Data JPA "who is the current user?" for audit fields (created_by, updated_by).
 *
 * HOW IT WORKS:
 * When an entity with @CreatedBy or @LastModifiedBy is saved, Spring calls getCurrentAuditor()
 * to determine the value. This implementation:
 *
 * 1. Checks if there's an authenticated user in the SecurityContext
 * 2. If the principal is a JWT (which it always is in our OAuth2 setup), returns jwt.getSubject()
 *    which is the Keycloak user UUID (e.g., "550e8400-e29b-41d4-a716-446655440000")
 * 3. Falls back to "system" for unauthenticated operations (like during tenant provisioning
 *    when the admin user record is created before their JWT exists)
 *
 * The bean name "auditorAware" is referenced by @EnableJpaAuditing(auditorAwareRef = "auditorAware")
 * in MultitenantApplication.java.
 */
@Component("auditorAware")
public class AuditorAwareImpl implements AuditorAware<String> {

    @Override
    public Optional<String> getCurrentAuditor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        // No authentication context = background process or startup operation
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.of("system");
        }

        // In our setup, authenticated users always have a JWT as the principal
        if (authentication.getPrincipal() instanceof Jwt jwt) {
            return Optional.ofNullable(jwt.getSubject()); // Keycloak user UUID
        }

        // Fallback for any other auth type (e.g., service accounts)
        return Optional.of(authentication.getName());
    }
}
