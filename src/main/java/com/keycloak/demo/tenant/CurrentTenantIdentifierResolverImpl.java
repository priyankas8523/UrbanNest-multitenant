package com.keycloak.demo.tenant.tenant;

import com.multitenant.app.common.constants.AppConstants;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Tells Hibernate "which tenant (schema) should I use for the current query?"
 *
 * HOW IT FITS IN THE MULTITENANCY PIPELINE:
 *   1. TenantFilter extracts tenant_id from JWT -> TenantContext.setTenantId("acme-corp")
 *   2. Controller method is called
 *   3. Repository method is called (e.g., companyUserRepository.findAll())
 *   4. Hibernate needs a DB connection -> asks SchemaMultiTenantConnectionProvider
 *   5. SchemaMultiTenantConnectionProvider asks THIS class: "what's the current tenant?"
 *   6. This class reads TenantContext.getTenantId() -> returns "tenant_acme-corp"
 *   7. SchemaMultiTenantConnectionProvider sets connection.setSchema("tenant_acme-corp")
 *   8. SQL query runs against the correct schema
 *
 * If no tenant is set (e.g., during startup or for master-schema queries),
 * returns "public" so queries hit the master schema (tenants table, subscription_plans).
 *
 * REGISTRATION NOTE: HibernatePropertiesCustomizer auto-registers this bean with Hibernate.
 * Without the customize() method, you'd need to manually configure it in application.yml.
 */
@Component
public class CurrentTenantIdentifierResolverImpl
        implements CurrentTenantIdentifierResolver<String>, HibernatePropertiesCustomizer {

    /**
     * Called by Hibernate before every database operation to determine the target schema.
     *
     * @return Schema name like "tenant_acme-corp" or "public" if no tenant context exists
     */
    @Override
    public String resolveCurrentTenantIdentifier() {
        String tenantId = TenantContext.getTenantId();
        if (tenantId != null) {
            return AppConstants.TENANT_SCHEMA_PREFIX + tenantId;  // e.g., "tenant_acme-corp"
        }
        return AppConstants.DEFAULT_SCHEMA;  // "public" schema for cross-tenant operations
    }

    /**
     * When true, Hibernate validates that existing sessions match the current tenant.
     * This prevents accidentally using a cached session from a different tenant.
     */
    @Override
    public boolean validateExistingCurrentSessions() {
        return true;
    }

    /** Auto-registers this resolver with Hibernate's properties during startup */
    @Override
    public void customize(Map<String, Object> hibernateProperties) {
        hibernateProperties.put(AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER, this);
    }
}
