package com.keycloak.demo.module.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Data Transfer Object for Client information returned in API responses.
 *
 * PURPOSE:
 * --------
 * This DTO decouples the internal Client JPA entity from the JSON sent to API consumers.
 * It deliberately omits sensitive or internal-only fields:
 *   - keycloakId  -- not exposed to prevent leaking identity provider internals
 *   - updatedAt, createdBy, updatedBy -- audit fields kept server-side only
 *
 * USAGE:
 * ------
 * - Returned by ClientService.mapToDto() and used across all client endpoints:
 *     GET /api/v1/clients/profile   -- client views their own profile
 *     GET /api/v1/clients           -- company staff lists all clients (paginated)
 *     GET /api/v1/clients/{id}      -- company staff views a single client
 *     PUT /api/v1/clients/{id}      -- admin updates a client (this DTO doubles as the
 *                                      request body; only non-null fields are applied)
 *
 * - The metadata map carries the same flexible JSONB data from the entity, allowing
 *   tenants to expose custom client attributes in the API without schema changes.
 *
 * - createdAt is included so the UI can display when the client registered.
 *
 * @Data (Lombok) generates getters, setters, equals, hashCode, and toString.
 * @Builder allows fluent construction in mapToDto() and tests.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClientDto {

    /** Internal database UUID of the client record. */
    private UUID id;

    /** Client's email address (also their login username in Keycloak). */
    private String email;

    /** Client's first name. */
    private String firstName;

    /** Client's last name. */
    private String lastName;

    /** Client's phone number. */
    private String phone;

    /** Whether the client account is currently active. */
    private boolean isActive;

    /**
     * Flexible key-value metadata from the JSONB column.
     * When used as an update request body, sending a non-null metadata map will
     * replace the entire metadata on the entity (not merge individual keys).
     */
    private Map<String, Object> metadata;

    /** Timestamp of when the client originally registered with the tenant. */
    private Instant createdAt;
}
