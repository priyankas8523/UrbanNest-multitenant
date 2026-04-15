package com.keycloak.demo.tenant;

import com.keycloak.demo.common.constants.AppConstants;
import com.keycloak.demo.config.LiquibaseConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

/**
 * Creates and destroys PostgreSQL schemas for individual tenants.
 *
 * Called during the tenant provisioning flow (plan purchase):
 *   SubscriptionService -> TenantProvisioningService -> TenantSchemaInitializer.createSchema("acme-corp")
 *
 * WHY WE USE RAW JDBC (NOT JdbcTemplate OR @Transactional) FOR DDL:
 *   CREATE SCHEMA must be committed BEFORE Liquibase opens its own connection.
 *   If we run CREATE SCHEMA inside the caller's @Transactional context, it stays
 *   uncommitted and Liquibase (using a separate pool connection) can't see the schema.
 *   Spring's REQUIRES_NEW doesn't work via self-invocation (AOP proxy is bypassed).
 *   The solution: get a raw connection directly from the DataSource, execute DDL,
 *   and commit manually -- completely outside Spring transaction management.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantSchemaInitializer {

    private final DataSource dataSource;
    private final LiquibaseConfig liquibaseConfig;

    /**
     * Creates a new PostgreSQL schema and runs all tenant Liquibase changesets.
     *
     * @param tenantId The tenant slug, e.g., "acme-corp" (creates schema "tenant_acme-corp")
     */
    public void createSchema(String tenantId) {
        String schemaName = AppConstants.TENANT_SCHEMA_PREFIX + tenantId;
        log.info("Creating tenant schema: {}", schemaName);

        // SECURITY: Validate tenant ID to prevent SQL injection.
        if (!tenantId.matches("^[a-z0-9_-]+$")) {
            throw new IllegalArgumentException("Invalid tenant ID format: " + tenantId);
        }

        // Step 1: CREATE SCHEMA using a raw connection with manual commit.
        // This guarantees the schema is visible to all subsequent connections
        // (including Liquibase's internal connection) before we call migrateTenantSchema().
        executeSchemaDDL("CREATE SCHEMA IF NOT EXISTS \"" + schemaName + "\"", schemaName);

        // Step 2: Run Liquibase changesets to create tables within the new schema
        liquibaseConfig.migrateTenantSchema(schemaName);

        log.info("Tenant schema '{}' created and migrated successfully", schemaName);
    }

    /**
     * Drops a tenant's schema entirely (saga compensation or tenant deletion).
     * CASCADE drops all tables, indexes, and data within the schema.
     */
    public void dropSchema(String tenantId) {
        String schemaName = AppConstants.TENANT_SCHEMA_PREFIX + tenantId;
        log.warn("Dropping tenant schema: {}", schemaName);

        if (!tenantId.matches("^[a-z0-9_-]+$")) {
            throw new IllegalArgumentException("Invalid tenant ID format: " + tenantId);
        }

        executeSchemaDDL("DROP SCHEMA IF EXISTS \"" + schemaName + "\" CASCADE", schemaName);
        log.info("Tenant schema '{}' dropped", schemaName);
    }

    /**
     * Executes a DDL statement on its own connection with autoCommit=true.
     * This ensures the DDL is immediately committed and visible to all other connections.
     *
     * We bypass Spring's transaction management entirely here because:
     * - DDL in PostgreSQL is transactional but must commit before Liquibase can see the schema
     * - Spring self-invocation bypasses AOP proxies, making @Transactional(REQUIRES_NEW) ineffective
     * - Using autoCommit=true is the simplest and most reliable way to guarantee immediate visibility
     */
    private void executeSchemaDDL(String sql, String schemaName) {
        try (Connection connection = dataSource.getConnection()) {
            boolean originalAutoCommit = connection.getAutoCommit();
            try {
                connection.setAutoCommit(true);
                try (Statement stmt = connection.createStatement()) {
                    stmt.execute(sql);
                }
                log.info("DDL executed and committed for schema: {}", schemaName);
            } finally {
                connection.setAutoCommit(originalAutoCommit);
            }
        } catch (Exception e) {
            throw new RuntimeException("Schema DDL failed for: " + schemaName, e);
        }
    }
}
