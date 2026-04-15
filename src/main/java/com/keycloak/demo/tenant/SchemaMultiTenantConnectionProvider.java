package com.keycloak.demo.tenant;

import com.keycloak.demo.common.constants.AppConstants;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;

/**
 * Provides Hibernate with tenant-aware database connections by switching the PostgreSQL schema.
 *
 * THIS IS WHERE THE ACTUAL DATA ISOLATION HAPPENS.
 *
 * HOW SCHEMA-PER-TENANT WORKS:
 * All tenants share ONE database and ONE connection pool. The difference is which
 * PostgreSQL "search_path" (schema) is active on each connection:
 *
 *   - Tenant "acme-corp" request -> connection.setSchema("tenant_acme-corp")
 *     -> SELECT * FROM company_users  ->  actually queries: tenant_acme-corp.company_users
 *
 *   - Tenant "globex" request -> connection.setSchema("tenant_globex")
 *     -> SELECT * FROM company_users  ->  actually queries: tenant_globex.company_users
 *
 *   - No tenant (master operations) -> connection.setSchema("public")
 *     -> SELECT * FROM tenants  ->  actually queries: public.tenants
 *
 * WHY THIS APPROACH:
 * - Database-per-tenant: Strongest isolation but requires N connection pools, N Liquibase runs
 *   on startup, and N backup jobs. Becomes an operational nightmare at 100+ tenants.
 * - Shared-schema with discriminator column: Simplest but risks data leaks (one bad WHERE clause
 *   exposes another tenant's data) and makes per-tenant backup/restore impossible.
 * - Schema-per-tenant (our choice): Good isolation (impossible to cross schemas accidentally),
 *   single connection pool, per-tenant backup via pg_dump -n schema_name, easy Liquibase migrations.
 *
 * IMPORTANT: When releasing a connection back to the pool, we reset the schema to "public".
 * This prevents the next thread that borrows this connection from accidentally operating
 * in the previous tenant's schema.
 */
@Slf4j
@Component
public class SchemaMultiTenantConnectionProvider
        implements MultiTenantConnectionProvider<String>, HibernatePropertiesCustomizer {

    private final DataSource dataSource;

    public SchemaMultiTenantConnectionProvider(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** Get a connection without any tenant context (used for master schema operations) */
    @Override
    public Connection getAnyConnection() throws SQLException {
        return dataSource.getConnection();
    }

    @Override
    public void releaseAnyConnection(Connection connection) throws SQLException {
        connection.close();
    }

    /**
     * Get a connection configured for a specific tenant.
     * Sets the PostgreSQL search_path to the tenant's schema so all unqualified
     * table references (e.g., "company_users") resolve to the correct schema.
     *
     * @param tenantIdentifier The schema name, e.g., "tenant_acme-corp"
     */
    @Override
    public Connection getConnection(String tenantIdentifier) throws SQLException {
        Connection connection = getAnyConnection();
        log.debug("Setting schema to: {}", tenantIdentifier);
        connection.setSchema(tenantIdentifier);  // PostgreSQL: SET search_path TO tenant_acme-corp
        return connection;
    }

    /**
     * Reset schema to "public" before returning the connection to the pool.
     * Without this, the next thread could accidentally operate in the wrong tenant's schema.
     */
    @Override
    public void releaseConnection(String tenantIdentifier, Connection connection) throws SQLException {
        connection.setSchema(AppConstants.DEFAULT_SCHEMA);  // Reset to "public"
        releaseAnyConnection(connection);
    }

    @Override
    public boolean supportsAggressiveRelease() {
        return false;  // Don't release connections aggressively - let the pool manage lifecycle
    }

    @Override
    public boolean isUnwrappableAs(Class<?> unwrapType) {
        return false;
    }

    @Override
    public <T> T unwrap(Class<T> unwrapType) {
        throw new UnsupportedOperationException("Cannot unwrap to " + unwrapType);
    }

    /** Auto-registers this provider with Hibernate's properties during startup */
    @Override
    public void customize(Map<String, Object> hibernateProperties) {
        hibernateProperties.put(AvailableSettings.MULTI_TENANT_CONNECTION_PROVIDER, this);
    }
}
