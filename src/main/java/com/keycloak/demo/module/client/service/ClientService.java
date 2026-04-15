package com.multitenant.app.module.client.service;

import com.multitenant.app.common.constants.RoleConstants;
import com.multitenant.app.common.exception.BusinessException;
import com.multitenant.app.common.exception.ResourceNotFoundException;
import com.multitenant.app.common.exception.TenantNotFoundException;
import com.multitenant.app.iam.IamService;
import com.multitenant.app.master.repository.TenantRepository;
import com.multitenant.app.module.auth.dto.ClientRegistrationRequest;
import com.multitenant.app.module.client.dto.ClientDto;
import com.multitenant.app.module.client.entity.Client;
import com.multitenant.app.module.client.repository.ClientRepository;
import com.multitenant.app.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * Core service for Client lifecycle management in the multitenant application.
 *
 * WHAT IS A CLIENT?
 * -----------------
 * A "Client" is an end-user who registers via the Client Portal to interact with
 * a specific company (tenant). Unlike CompanyUsers (ADMIN, ROLE1, ROLE2) who manage
 * the tenant, Clients are the external users that the tenant serves. Each client's
 * data is stored in the tenant's isolated PostgreSQL schema.
 *
 * KEY RESPONSIBILITIES:
 * ---------------------
 *   1. registerClient  -- Full self-registration flow (public endpoint, no JWT):
 *                         validates tenant, sets TenantContext manually, creates
 *                         Keycloak user with CLIENT role, adds to tenant group,
 *                         persists Client record in the tenant schema.
 *   2. getProfileByKeycloakId -- Load a client's own profile from their JWT subject.
 *   3. listClients    -- Paginated listing for company staff (ADMIN/ROLE1/ROLE2).
 *   4. getClientById  -- Single client lookup by database ID.
 *   5. updateClient   -- Partial update of mutable fields (name, phone, metadata).
 *
 * TENANT CONTEXT HANDLING:
 * ------------------------
 * For authenticated endpoints (profile, list, detail, update), the tenant context is
 * already set by the TenantFilter which reads the tenant_id from the JWT. However,
 * registerClient is called from a public (unauthenticated) endpoint, so it must set
 * TenantContext manually using the tenantId path parameter and clear it in a finally
 * block to prevent tenant leakage on the thread.
 *
 * KEYCLOAK INTEGRATION:
 * ---------------------
 * During registration, three Keycloak operations happen in sequence:
 *   1. Create the user in Keycloak (email as username, with tenant_id + portal_type attributes)
 *   2. Assign the CLIENT realm role (so @PreAuthorize checks can identify client users)
 *   3. Add the user to the tenant's Keycloak group (e.g., "tenant_acme") for group-based policies
 *
 * If any of these steps fail, the @Transactional annotation will roll back the database
 * changes, but the Keycloak user may already be created (Keycloak calls are not part of
 * the DB transaction). In production, you may want compensating logic or an outbox pattern.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClientService {

    private final ClientRepository clientRepository;

    /** TenantRepository queries the master/public schema to validate tenant existence. */
    private final TenantRepository tenantRepository;

    /** Unified IAM facade for all Keycloak operations (user, group, role management). */
    private final IamService iamService;

    /**
     * Full client self-registration flow.
     *
     * IMPORTANT -- MANUAL TENANT CONTEXT:
     * This method is invoked from a public (unauthenticated) registration endpoint,
     * so there is no JWT and the TenantFilter has not set TenantContext. We must:
     *   1. Set TenantContext.setTenantId(tenantId) manually so that Hibernate routes
     *      all DB operations to the correct tenant schema.
     *   2. Clear it in a finally block to avoid polluting the thread for subsequent requests
     *      (important in thread-pool environments like Tomcat).
     *
     * FLOW:
     *   1. Validate the tenant exists in the master schema (TenantRepository).
     *   2. Check for duplicate email within the tenant schema.
     *   3. Create the user in Keycloak with email, password, name, and custom attributes
     *      (tenant_id and portal_type="client" for downstream identification).
     *   4. Assign the CLIENT realm role so role-based access control works.
     *   5. Add the user to the tenant's Keycloak group ("tenant_{tenantId}") for
     *      group-based policies and easy tenant-level user management in Keycloak.
     *   6. Persist the Client entity in the tenant's schema with the keycloakId link.
     *
     * @param tenantId the tenant identifier (e.g., "acme") from the registration URL path
     * @param request  the registration payload (email, password, firstName, lastName, phone)
     * @return ClientDto of the newly created client
     * @throws TenantNotFoundException if the tenantId does not match any registered tenant
     * @throws BusinessException       if a client with the same email already exists
     */
    @Transactional
    public ClientDto registerClient(String tenantId, ClientRegistrationRequest request) {
        // Step 1: Validate the tenant exists in the master/public schema.
        // This check runs against the master DB (not tenant schema) because TenantRepository
        // is configured for the public schema. Prevents registration against non-existent tenants.
        if (!tenantRepository.existsByTenantId(tenantId)) {
            throw new TenantNotFoundException(tenantId);
        }

        // Step 2: Set tenant context manually so all subsequent DB calls (via ClientRepository)
        // are routed to this tenant's schema. Without this, Hibernate would not know which
        // schema to target since there is no JWT to extract tenant info from.
        TenantContext.setTenantId(tenantId);
        try {
            // Step 3: Duplicate email check within the tenant's schema.
            // Two different tenants CAN have the same email (different schemas), but within
            // a single tenant, emails must be unique.
            if (clientRepository.existsByEmail(request.getEmail())) {
                throw new BusinessException("Client with this email already exists");
            }

            // Step 4: Create the user in Keycloak.
            // The custom attributes (tenant_id, portal_type) are stored in Keycloak's user
            // attributes and are included in the JWT after login. The TenantFilter uses
            // tenant_id to route authenticated requests, and portal_type distinguishes
            // client-portal users from company-portal users.
            String keycloakUserId = iamService.createUser(
                    request.getEmail(),
                    request.getPassword(),
                    request.getFirstName(),
                    request.getLastName(),
                    Map.of("tenant_id", tenantId, "portal_type", "client")
            );

            // Step 5: Assign the CLIENT realm role in Keycloak.
            // This role is checked by @PreAuthorize annotations on controllers. Client users
            // get the CLIENT role (not ADMIN/ROLE1/ROLE2), which restricts them to
            // client-portal endpoints only (e.g., viewing their own profile).
            iamService.assignRealmRole(keycloakUserId, RoleConstants.CLIENT);

            // Step 6: Add user to the tenant's Keycloak group.
            // Groups follow the naming convention "tenant_{tenantId}" (e.g., "tenant_acme").
            // This enables group-based policies in Keycloak and makes it easy to enumerate
            // all users belonging to a tenant directly from Keycloak's admin console.
            String groupName = "tenant_" + tenantId;
            String groupId = iamService.getGroupIdByName(groupName);
            iamService.addUserToGroup(keycloakUserId, groupId);

            // Step 7: Persist the Client record in the tenant's schema.
            // The keycloakId field links this DB record to the Keycloak identity, enabling
            // profile lookups from the JWT subject claim. isActive defaults to true.
            Client client = Client.builder()
                    .keycloakId(keycloakUserId)
                    .email(request.getEmail())
                    .firstName(request.getFirstName())
                    .lastName(request.getLastName())
                    .phone(request.getPhone())
                    .isActive(true)
                    .build();
            client = clientRepository.save(client);

            log.info("Client '{}' registered under tenant '{}'", request.getEmail(), tenantId);
            return mapToDto(client);

        } finally {
            // CRITICAL: Always clear TenantContext to prevent tenant ID from leaking to
            // other requests that may reuse this thread from the Tomcat thread pool.
            TenantContext.clear();
        }
    }

    /**
     * Retrieve the currently logged-in client's profile using their Keycloak ID (JWT "sub" claim).
     *
     * This is called from the /profile endpoint. The tenant context is already set by the
     * TenantFilter (since this is an authenticated endpoint), so the query automatically
     * targets the correct tenant schema.
     *
     * @param keycloakId the JWT subject claim identifying the client in Keycloak
     * @return ClientDto with the client's profile data
     * @throws ResourceNotFoundException if no Client record matches the keycloakId
     */
    @Transactional(readOnly = true)
    public ClientDto getProfileByKeycloakId(String keycloakId) {
        Client client = clientRepository.findByKeycloakId(keycloakId)
                .orElseThrow(() -> new ResourceNotFoundException("Client", "keycloakId", keycloakId));
        return mapToDto(client);
    }

    /**
     * Paginated listing of all clients in the current tenant's schema.
     *
     * Used by company staff (ADMIN, ROLE1, ROLE2) to browse their clients.
     * Tenant scoping is automatic -- the TenantFilter has already set the schema,
     * so findAll() only returns clients belonging to the authenticated user's tenant.
     *
     * @param pageable pagination and sorting parameters (default page size is 20)
     * @return a Page of ClientDto objects
     */
    @Transactional(readOnly = true)
    public Page<ClientDto> listClients(Pageable pageable) {
        return clientRepository.findAll(pageable).map(this::mapToDto);
    }

    /**
     * Retrieve a single client by their database UUID.
     *
     * Used by company staff to view details of a specific client. The query is
     * automatically scoped to the current tenant's schema by the connection provider.
     *
     * @param clientId the UUID primary key of the client
     * @return ClientDto with the client's data
     * @throws ResourceNotFoundException if no Client record exists with this ID
     */
    @Transactional(readOnly = true)
    public ClientDto getClientById(UUID clientId) {
        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> new ResourceNotFoundException("Client", "id", clientId));
        return mapToDto(client);
    }

    /**
     * Partial update of a client's mutable fields.
     *
     * Only non-null fields in the updateDto are applied, allowing callers to send only
     * the fields they want to change. Immutable fields (email, keycloakId) are not
     * updated here -- email changes would require Keycloak synchronization.
     *
     * Note: metadata replacement is full (not a merge). If the caller sends a metadata
     * map, it replaces the entire existing metadata. To add a single key, the caller
     * should read the current metadata, add the key, and send the full map back.
     *
     * @param clientId  the UUID of the client to update
     * @param updateDto DTO containing the fields to update (null fields are skipped)
     * @return ClientDto with the updated client data
     * @throws ResourceNotFoundException if no Client record exists with this ID
     */
    @Transactional
    public ClientDto updateClient(UUID clientId, ClientDto updateDto) {
        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> new ResourceNotFoundException("Client", "id", clientId));

        // Only update fields that were explicitly provided (non-null) in the request
        if (updateDto.getFirstName() != null) client.setFirstName(updateDto.getFirstName());
        if (updateDto.getLastName() != null) client.setLastName(updateDto.getLastName());
        if (updateDto.getPhone() != null) client.setPhone(updateDto.getPhone());
        if (updateDto.getMetadata() != null) client.setMetadata(updateDto.getMetadata());

        client = clientRepository.save(client);
        return mapToDto(client);
    }

    /**
     * Maps a Client entity to a ClientDto for API responses.
     *
     * Deliberately excludes internal fields (keycloakId, updatedAt, createdBy, updatedBy)
     * from the response to avoid exposing implementation details to API consumers.
     * The createdAt timestamp is included so the UI can show when the client registered.
     */
    private ClientDto mapToDto(Client client) {
        return ClientDto.builder()
                .id(client.getId())
                .email(client.getEmail())
                .firstName(client.getFirstName())
                .lastName(client.getLastName())
                .phone(client.getPhone())
                .isActive(client.isActive())
                .metadata(client.getMetadata())
                .createdAt(client.getCreatedAt())
                .build();
    }
}
