package com.keycloak.demo.tenant;

/**
 * Purpose:
 * Stores tenant for the current request thread
 *
 * Example:
 * tenant_101
 * tenant_102
 */
public class TenantContext {

    private static final ThreadLocal<String> TENANT = new ThreadLocal<>();

    public static void setTenant(String tenant){
        TENANT.set(tenant);
    }

    public static String getTenant() {
        return TENANT.get();
    }

    public static void clear(){
        TENANT.remove();
    }
}
