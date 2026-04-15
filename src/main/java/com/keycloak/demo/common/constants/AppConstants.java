package com.multitenant.app.common.constants;

/**
 * Application-wide constants used across all layers.
 *
 * WHY: Centralizing these values prevents magic strings scattered through the codebase.
 * If we ever change the API version prefix or tenant header name, we change it once here.
 */
public final class AppConstants {

    // Private constructor prevents instantiation - this is a constants-only class
    private AppConstants() {}

    // -- DATABASE SCHEMA CONSTANTS --

    // The "public" PostgreSQL schema holds cross-tenant data (tenant registry, subscription plans)
    public static final String DEFAULT_SCHEMA = "public";

    // Every tenant gets a schema named "tenant_{slug}" (e.g., tenant_acme-corp)
    // This prefix is used by Hibernate's multi-tenant connection provider to route queries
    public static final String TENANT_SCHEMA_PREFIX = "tenant_";

    // -- HTTP HEADER FOR TENANT RESOLUTION --

    // For unauthenticated requests (like client registration), we identify the tenant
    // via this custom HTTP header. For authenticated requests, tenant_id comes from the JWT instead.
    public static final String TENANT_HEADER = "X-Tenant-ID";

    // -- API PATH CONSTANTS --
    // Defined here so controllers use the same base paths and SecurityConfig can reference them
    // to configure which endpoints are public vs protected.

    public static final String API_V1 = "/v1";
    public static final String AUTH_PATH = API_V1 + "/auth";                    // Base for all auth endpoints
    public static final String COMPANY_AUTH_PATH = AUTH_PATH + "/company";       // Company register/login/logout
    public static final String CLIENT_AUTH_PATH = AUTH_PATH + "/client";         // Client register/login/logout
    public static final String TENANT_PATH = API_V1 + "/tenants";               // SUPER_ADMIN tenant management
    public static final String COMPANY_PATH = API_V1 + "/company";              // Company portal (users, roles)
    public static final String CLIENT_PATH = API_V1 + "/clients";               // Client portal (profile, listing)
    public static final String SUBSCRIPTION_PATH = API_V1 + "/subscriptions";   // Subscription purchase + management
}
