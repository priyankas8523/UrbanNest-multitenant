package com.keycloak.demo.config;

import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.sql.Connection;

/**
 * Custom Liquibase configuration for per-tenant schema migrations.
 *
 * HOW DATABASE MIGRATIONS WORK IN THIS APP:
 *
 * There are TWO sets of Liquibase changelogs:
 *
 * 1. MASTER CHANGELOG (automatic on startup):
 *    File:    db/changelog/master/master.yaml
 *    Target:  "public" schema
 *    Contains: tenants table, subscription_plans table
 *    Runs:    Automatically via Spring Boot's Liquibase auto-configuration
 *
 * 2. TENANT CHANGELOG (on-demand per tenant):
 *    File:    db/changelog/tenant/tenant.yaml
 *    Target:  "tenant_{slug}" schema (e.g., tenant_acme-corp)
 *    Contains: company_users, roles, clients, audit_log tables + default role seeds
 *    Runs:    Programmatically via this class when a company purchases a plan
 *
 * FLOW: Admin buys plan -> SubscriptionService -> TenantProvisioningService
 *       -> TenantSchemaInitializer.createSchema("acme-corp")
 *       -> LiquibaseConfig.migrateTenantSchema("tenant_acme-corp")
 *       -> Runs all changesets from tenant.yaml inside that schema
 *
 * FOR EXISTING TENANTS (when you add a new changeset):
 *   Add a new file (e.g., 002-add-department.yaml) + include it in tenant.yaml.
 *   Call migrateAllExistingTenants() on startup. Liquibase checks its
 *   DATABASECHANGELOG table and only applies new changesets.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class LiquibaseConfig {

    private final DataSource dataSource;  // Same connection pool used by the app

    /**
     * Runs Liquibase changelog for a specific tenant schema.
     * Called by TenantSchemaInitializer.createSchema() during plan purchase.
     *
     * HOW IT WORKS:
     *   1. Gets a raw JDBC connection from the pool
     *   2. Sets the connection's schema to the target tenant schema
     *   3. Creates a Liquibase instance pointing to the tenant changelog
     *   4. Runs update() which applies all unapplied changesets
     *   5. Liquibase tracks applied changesets in DATABASECHANGELOG table (inside tenant schema)
     *   6. Connection is closed and returned to pool
     *
     * @param schemaName The full schema name, e.g., "tenant_acme-corp"
     */
    public void migrateTenantSchema(String schemaName) {
        log.info("Running Liquibase migration for tenant schema: {}", schemaName);
        try (Connection connection = dataSource.getConnection()) {
            // Use SET search_path with quoted identifier instead of connection.setSchema().
            // JDBC's setSchema() does not quote the schema name, so PostgreSQL rejects
            // hyphenated names (e.g., tenant_acme-corporation) without quotes.
            try (java.sql.Statement stmt = connection.createStatement()) {
                stmt.execute("SET search_path TO \"" + schemaName + "\"");
            }

            // Create Liquibase database wrapper using the schema-scoped connection
            Database database = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(connection));
            database.setDefaultSchemaName(schemaName);
            database.setLiquibaseSchemaName(schemaName);  // Store DATABASECHANGELOG inside tenant schema

            // Run all unapplied changesets from the tenant changelog
            Liquibase liquibase = new Liquibase(
                    "db/changelog/tenant/tenant.yaml",
                    new ClassLoaderResourceAccessor(),
                    database
            );
            liquibase.update("");  // Empty string = no context filter, run all changesets

            log.info("Liquibase migration completed for schema: {}", schemaName);
        } catch (Exception e) {
            log.error("Liquibase migration failed for schema: {}", schemaName, e);
            throw new RuntimeException("Tenant schema migration failed: " + schemaName, e);
        }
    }
}
