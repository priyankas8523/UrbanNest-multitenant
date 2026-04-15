package com.multitenant.app.module.client.entity;

import com.multitenant.app.common.audit.AuditEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;
import java.util.UUID;

/**
 * JPA entity representing a Client (end-user) within a tenant's isolated database schema.
 *
 * MULTITENANCY CONTEXT:
 * ---------------------
 * Clients are people who register through the Client Portal to use a specific company's
 * (tenant's) services. Each tenant has its own PostgreSQL schema (e.g., "tenant_acme"),
 * and the "clients" table lives inside that schema. This means:
 *   - Client records are physically isolated per tenant -- one tenant can never see
 *     another tenant's clients.
 *   - The active schema is determined at runtime by TenantContext, which Hibernate's
 *     multi-tenant connection provider uses to route SQL to the correct schema.
 *
 * INHERITANCE -- extends AuditEntity:
 * ------------------------------------
 * AuditEntity is a @MappedSuperclass that contributes four columns to this table:
 *   - created_at  (Instant) -- auto-set on INSERT by Spring Data's AuditingEntityListener
 *   - updated_at  (Instant) -- auto-set on every UPDATE
 *   - created_by  (String)  -- Keycloak user ID (JWT subject) who created this record
 *   - updated_by  (String)  -- Keycloak user ID who last modified this record
 * These fields are populated automatically via AuditorAwareImpl and never need to be
 * set manually in application code.
 *
 * KEYCLOAK LINK:
 * --------------
 * The keycloakId field ties this database record to the user's identity in Keycloak.
 * When a client logs in, the JWT "sub" claim equals this keycloakId, allowing the
 * application to look up the Client record from a token without exposing internal
 * database IDs in the auth layer.
 *
 * JSONB METADATA:
 * ---------------
 * The metadata field is stored as a PostgreSQL JSONB column, enabling each tenant to
 * attach arbitrary key-value data to a client without requiring schema migrations.
 * Example uses: {"referral_source": "google", "plan": "premium", "notes": "VIP"}.
 * This is especially useful in a multitenant system where different tenants have
 * different data requirements for their clients.
 */
@Entity
@Table(name = "clients")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Client extends AuditEntity {

    /**
     * Primary key -- auto-generated UUID.
     * Using UUIDs (instead of auto-increment longs) avoids ID collisions across schemas
     * and makes IDs non-guessable, which is important for client-facing APIs.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * The Keycloak user ID (the "sub" claim from the JWT).
     * This is the bridge between the identity provider and the tenant-scoped database record.
     * Marked unique + not-null because every Client must have exactly one Keycloak identity.
     * Used by getProfileByKeycloakId() to load the client's own profile from their JWT.
     */
    @Column(name = "keycloak_id", unique = true, nullable = false)
    private String keycloakId;

    /**
     * Client's email address. Also unique within the tenant schema and used during
     * registration to detect duplicate sign-ups (existsByEmail check in ClientService).
     */
    @Column(unique = true, nullable = false)
    private String email;

    /** Client's first name. Limited to 100 characters at the database level. */
    @Column(name = "first_name", length = 100)
    private String firstName;

    /** Client's last name. Limited to 100 characters at the database level. */
    @Column(name = "last_name", length = 100)
    private String lastName;

    /** Phone number. Limited to 20 characters to accommodate international formats. */
    @Column(length = 20)
    private String phone;

    /**
     * Soft-delete / deactivation flag. Defaults to true on creation.
     * When set to false, the client account is considered disabled but the record is
     * preserved for audit history. The repository provides findByIsActive() to filter.
     *
     * @Builder.Default ensures the Lombok builder initializes this to true rather than
     * the Java primitive default of false.
     */
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean isActive = true;

    /**
     * Flexible JSONB column for tenant-specific client data.
     *
     * Stored as PostgreSQL "jsonb" (binary JSON), which supports indexing and efficient
     * querying. Hibernate maps it via @JdbcTypeCode(SqlTypes.JSON) so the Java
     * Map<String, Object> is automatically serialized/deserialized to/from JSONB.
     *
     * This avoids the need for extra columns or EAV tables when different tenants
     * want to track different attributes on their clients (e.g., loyalty tier,
     * custom tags, preferences).
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> metadata;
}
