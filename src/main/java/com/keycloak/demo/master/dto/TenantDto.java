package com.multitenant.app.master.dto;

import com.multitenant.app.common.enums.TenantStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for tenant information returned to API callers.
 *
 * This DTO deliberately EXCLUDES sensitive internal fields that exist on the Tenant entity:
 *   - schemaName:       Exposing the DB schema name is a security risk (helps attackers target schemas).
 *   - ownerKeycloakId:  Internal Keycloak UUID -- no reason for API consumers to see this.
 *   - subscriptionPlanId: Internal reference; plan details should be fetched via a dedicated endpoint.
 *   - updatedAt:        Not useful for external consumers; createdAt is sufficient.
 *
 * By using a DTO instead of returning the entity directly, we:
 *   1. Control exactly what data leaves the system (defense in depth).
 *   2. Decouple the API contract from the database schema -- we can change columns without breaking clients.
 *   3. Avoid accidental lazy-loading of JPA relationships in the serialization layer.
 *
 * Used in: TenantController responses, TenantProvisioningService return values.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantDto {

    /** Internal UUID of the tenant. Included so SUPER_ADMIN tools can reference it if needed. */
    private UUID id;

    /** The human-readable slug (e.g., "acme-corp"). Primary identifier in API paths and displays. */
    private String tenantId;

    /** Company display name as originally registered. */
    private String companyName;

    /** Current lifecycle status: PROVISIONING, ACTIVE, SUSPENDED, or DELETED. */
    private TenantStatus status;

    /** Email of the company owner. Useful for SUPER_ADMIN to contact tenant administrators. */
    private String ownerEmail;

    /** When the tenant was first registered. Shown as "member since" in dashboards. */
    private Instant createdAt;
}
