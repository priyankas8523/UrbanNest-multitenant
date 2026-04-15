package com.keycloak.demo.tenant;

import com.keycloak.demo.common.constants.AppConstants;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * HTTP filter that extracts the tenant ID from every request and stores it in TenantContext.
 *
 * This filter is registered in SecurityConfig to run BEFORE Spring Security's
 * UsernamePasswordAuthenticationFilter, so the tenant context is available early in the chain.
 *
 * TENANT RESOLUTION STRATEGY (two sources, in priority order):
 *
 *   1. JWT "tenant_id" claim (for authenticated requests):
 *      The Keycloak JWT contains a custom claim "tenant_id" that was set as a user attribute
 *      during registration. This is TAMPER-PROOF because the JWT is signed by Keycloak.
 *      A user with tenant_id="acme" CANNOT pretend to be tenant_id="globex".
 *
 *   2. X-Tenant-ID HTTP header (for unauthenticated requests):
 *      Used only for client registration (POST /v1/auth/client/register) where there's
 *      no JWT yet. The header tells us which company the client wants to register with.
 *      This is validated against the tenant registry before any data is created.
 *
 * LIFECYCLE PER REQUEST:
 *   doFilterInternal() {
 *     try {
 *       tenantId = resolveTenantId()     // Extract from JWT or header
 *       TenantContext.setTenantId()       // Store in ThreadLocal
 *       filterChain.doFilter()            // Continue to controller -> service -> repository
 *     } finally {
 *       TenantContext.clear()             // ALWAYS clean up to prevent leakage
 *     }
 *   }
 */
@Slf4j
@Component
public class TenantFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            String tenantId = resolveTenantId(request);
            if (tenantId != null) {
                TenantContext.setTenantId(tenantId);
            }
            filterChain.doFilter(request, response);
        } finally {
            // CRITICAL: Always clear to prevent tenant context leaking to the next request
            // (threads are reused by the thread pool)
            TenantContext.clear();
        }
    }

    /**
     * Determines the tenant ID from the most trustworthy source available.
     * JWT claim is preferred over header because it's cryptographically signed.
     */
    private String resolveTenantId(HttpServletRequest request) {
        // Priority 1: Extract from JWT (authenticated requests - tamper-proof)
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            Jwt jwt = jwtAuth.getToken();
            String tenantId = jwt.getClaimAsString("tenant_id");
            if (tenantId != null) {
                log.debug("Resolved tenant '{}' from JWT", tenantId);
                return tenantId;
            }
        }

        // Priority 2: Fallback to X-Tenant-ID header (unauthenticated requests like client registration)
        String headerTenantId = request.getHeader(AppConstants.TENANT_HEADER);
        if (headerTenantId != null && !headerTenantId.isBlank()) {
            log.debug("Resolved tenant '{}' from header", headerTenantId);
            return headerTenantId;
        }

        // No tenant context (e.g., company registration creates a NEW tenant, doesn't need an existing one)
        return null;
    }

    /**
     * Skip tenant resolution for endpoints that don't need it:
     * - Company auth (register/login create or authenticate a tenant, not operate within one)
     * - Tenant registration (creates a new tenant)
     * - Actuator/Swagger (infrastructure endpoints)
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return path.startsWith("/v1/auth/company")
                || path.equals("/v1/tenants/register")
                || path.startsWith("/actuator")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/swagger-ui");
    }
}
