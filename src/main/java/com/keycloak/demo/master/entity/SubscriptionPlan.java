package com.multitenant.app.master.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * SubscriptionPlan Entity -- Defines the tiers/plans that tenants can subscribe to.
 *
 * Lives in the PUBLIC (master) schema alongside the tenants table. Each plan defines
 * hard limits on what a tenant can do (how many users, how many clients) and a flexible
 * set of feature flags stored as JSONB.
 *
 * Example plans: "Free", "Starter", "Professional", "Enterprise".
 *
 * A tenant's subscription_plan_id in the Tenant table points to a row here.
 * The application checks these limits at runtime -- for example, before creating a new
 * user in a tenant schema, the system verifies that the tenant hasn't exceeded maxUsers
 * for their current plan.
 */
@Entity
// Pinned to the "public" schema because subscription plans are global/shared data,
// not per-tenant. Every tenant references the same set of plans.
@Table(name = "subscription_plans", schema = "public")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionPlan {

    /**
     * Internal primary key. Referenced by Tenant.subscriptionPlanId.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * Display name of the plan (e.g., "Free", "Professional", "Enterprise").
     * Used for lookups and displayed in the UI. Should be unique in practice,
     * though the DB constraint is not enforced here -- the repository lookup
     * findByName() assumes uniqueness.
     */
    @Column(nullable = false, length = 100)
    private String name;

    /**
     * Maximum number of users this plan allows per tenant.
     * Enforced at the application level when creating new CompanyUser records.
     * For example, a "Starter" plan might allow 10 users, "Enterprise" might allow 500.
     * A value of -1 or Integer.MAX_VALUE could represent "unlimited" depending on convention.
     */
    @Column(name = "max_users", nullable = false)
    private int maxUsers;

    /**
     * Maximum number of clients (customers/accounts) this plan allows per tenant.
     * Similar enforcement pattern to maxUsers -- checked before creating new client records.
     * This lets you tier the plans by business volume, not just headcount.
     */
    @Column(name = "max_clients", nullable = false)
    private int maxClients;

    /**
     * Flexible feature flags and configuration stored as PostgreSQL JSONB.
     *
     * Using JSONB instead of separate columns because:
     *   - Feature sets change frequently across plans without needing DB migrations.
     *   - Different plans can have completely different feature keys.
     *   - JSONB supports efficient querying in PostgreSQL if needed.
     *
     * Example value:
     *   {
     *     "analytics": true,
     *     "api_access": true,
     *     "export_formats": ["csv", "pdf", "excel"],
     *     "custom_branding": false,
     *     "max_storage_gb": 50
     *   }
     *
     * The @JdbcTypeCode(SqlTypes.JSON) tells Hibernate to use the JSONB JDBC type,
     * and the columnDefinition ensures the DDL creates a proper jsonb column in PostgreSQL.
     * Hibernate serializes/deserializes this as a Java Map automatically.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> features;

    /**
     * Whether this plan is currently available for new subscriptions.
     * Setting this to false hides the plan from new signups without affecting
     * existing tenants already on this plan (grandfathering).
     */
    @Column(name = "is_active", nullable = false)
    private boolean isActive;

    /**
     * When this plan was first created. Immutable after insert.
     */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Last time this plan's details were modified (e.g., limit changes, feature updates).
     */
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
