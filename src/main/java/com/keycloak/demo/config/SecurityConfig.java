package com.multitenant.app.config;

import com.multitenant.app.common.constants.AppConstants;
import com.multitenant.app.tenant.TenantFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Core security configuration. Defines HOW requests are authenticated and authorized.
 *
 * ARCHITECTURE: We use two separate filter chains (ordered by priority):
 *
 *   Chain 1 (publicFilterChain, @Order(1)):
 *     Matches: /auth/**, /tenants/register, /actuator/health, /swagger-ui/**
 *     Action:  No JWT required, anyone can access (registration, login, docs)
 *
 *   Chain 2 (protectedFilterChain, @Order(2)):
 *     Matches: Everything else (/company/**, /clients/**, /tenants/**)
 *     Action:  Requires valid JWT from Keycloak, runs TenantFilter to set tenant context
 *
 * WHY TWO CHAINS: Spring Security evaluates chains in order. If a request matches
 * Chain 1 (public), it skips Chain 2 entirely. This prevents the JWT validator from
 * rejecting unauthenticated registration/login requests.
 *
 * @EnableMethodSecurity - Activates @PreAuthorize annotations on controller methods.
 *   Without this, @PreAuthorize("hasRole('ADMIN')") would be silently ignored.
 *
 * KEY SECURITY DECISIONS:
 * - CSRF disabled: This is a stateless REST API (no cookies/sessions), so CSRF is N/A
 * - Sessions STATELESS: Every request is authenticated via JWT, no server-side session
 * - CORS configured: Allows frontend SPAs on different ports to call this API
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthConverter jwtAuthConverter;  // Extracts roles from Keycloak JWT
    private final TenantFilter tenantFilter;          // Sets TenantContext from JWT's tenant_id claim

    @Value("${app.cors.allowed-origins}")
    private String allowedOrigins;  // e.g., "http://localhost:3000,http://localhost:3001"

    // Endpoints that don't require any authentication
    private static final String[] PUBLIC_ENDPOINTS = {
            AppConstants.AUTH_PATH + "/**",               // /v1/auth/company/**, /v1/auth/client/**
            AppConstants.TENANT_PATH + "/register",        // /v1/tenants/register
            AppConstants.SUBSCRIPTION_PATH + "/plans",     // /v1/subscriptions/plans (view plans before login)
            "/actuator/health",                            // Health check for load balancers
            "/v3/api-docs/**",                             // OpenAPI spec JSON
            "/swagger-ui/**",                              // Swagger UI assets
            "/swagger-ui.html"                             // Swagger UI entry point
    };

    /**
     * CHAIN 1: Public endpoints - no JWT required.
     * @Order(1) means this is evaluated FIRST. If the request URL matches
     * any PUBLIC_ENDPOINT pattern, this chain handles it and Chain 2 is skipped.
     */
    @Bean
    @Order(1)
    public SecurityFilterChain publicFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher(PUBLIC_ENDPOINTS)      // Only apply to these URL patterns
                .csrf(AbstractHttpConfigurer::disable)   // No CSRF for stateless API
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());  // Allow everyone
        return http.build();
    }

    /**
     * CHAIN 2: Protected endpoints - valid Keycloak JWT required.
     * @Order(2) means this catches everything that Chain 1 didn't match.
     *
     * Request flow through this chain:
     *   1. JWT is extracted from "Authorization: Bearer <token>" header
     *   2. JWT signature is verified against Keycloak's JWKS endpoint (configured in application.yml)
     *   3. JwtAuthConverter extracts realm roles from JWT -> Spring Security authorities
     *   4. TenantFilter extracts tenant_id from JWT -> sets TenantContext for Hibernate
     *   5. @PreAuthorize on the controller method checks if the user has the required role
     *   6. Hibernate routes the DB query to the correct tenant schema
     */
    @Bean
    @Order(2)
    public SecurityFilterChain protectedFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .oauth2ResourceServer(oauth2 -> oauth2
                        // Use our custom converter instead of Spring's default (which doesn't know Keycloak's JWT structure)
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthConverter))
                )
                // TenantFilter runs AFTER BearerTokenAuthenticationFilter so the JWT is already
                // validated and present in SecurityContext when TenantFilter reads tenant_id from it.
                .addFilterAfter(tenantFilter, BearerTokenAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().authenticated()  // Must have a valid JWT (specific roles checked by @PreAuthorize)
                );
        return http.build();
    }

    /**
     * Prevent Spring Boot from auto-registering TenantFilter in the main servlet filter chain.
     *
     * WHY THIS IS NECESSARY:
     * TenantFilter is a @Component, so Spring Boot auto-detects it and registers it as a
     * plain servlet filter that runs BEFORE the Spring Security filter chain. At that point,
     * the JWT has NOT been validated yet (BearerTokenAuthenticationFilter hasn't run), so
     * SecurityContextHolder is empty and TenantFilter can't extract tenant_id from the JWT.
     * TenantFilter's finally block then clears TenantContext. Later, when the security chain
     * tries to invoke TenantFilter via addFilterAfter, OncePerRequestFilter sees the filter
     * already ran this request and skips it — leaving TenantContext null for the whole request.
     *
     * By disabling auto-registration here, TenantFilter ONLY runs within the Spring Security
     * filter chain (via addFilterAfter above), where BearerTokenAuthenticationFilter has
     * already validated the JWT and populated SecurityContextHolder.
     */
    @Bean
    public FilterRegistrationBean<TenantFilter> tenantFilterRegistration(TenantFilter filter) {
        FilterRegistrationBean<TenantFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);  // Don't register in main servlet chain; Spring Security chain only
        return registration;
    }

    /**
     * CORS configuration for frontend SPAs.
     * Both portals (Company on :3000, Client on :3001) need to call this API
     * from a different origin, so we must explicitly allow cross-origin requests.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.asList(allowedOrigins.split(",")));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));         // Allow Authorization header, X-Tenant-ID, etc.
        configuration.setAllowCredentials(true);                // Allow cookies/auth headers
        configuration.setMaxAge(3600L);                         // Cache preflight response for 1 hour

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration); // Apply to all endpoints
        return source;
    }
}
