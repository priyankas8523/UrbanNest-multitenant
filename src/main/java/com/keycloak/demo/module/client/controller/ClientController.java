package com.multitenant.app.module.client.controller;

import com.multitenant.app.common.constants.AppConstants;
import com.multitenant.app.common.constants.RoleConstants;
import com.multitenant.app.common.dto.ApiResponse;
import com.multitenant.app.common.dto.PagedResponse;
import com.multitenant.app.module.client.dto.ClientDto;
import com.multitenant.app.module.client.service.ClientService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST controller for Client Portal endpoints.
 *
 * BASE PATH: /api/v1/clients (defined by AppConstants.CLIENT_PATH)
 *
 * AUDIENCE:
 * ---------
 * This controller serves two distinct audiences within the same tenant:
 *
 *   1. CLIENTS (end-users):
 *      - GET /profile -- A client retrieves their own profile using the JWT subject.
 *        No role restriction beyond being authenticated, because every logged-in client
 *        should be able to see their own data.
 *
 *   2. COMPANY STAFF (ADMIN, ROLE1, ROLE2):
 *      - GET /           -- List all clients in the tenant (paginated).
 *      - GET /{clientId} -- View a specific client's details.
 *      - PUT /{clientId} -- Update a client's details (ADMIN only).
 *      These endpoints let company users manage and review their tenant's clients.
 *
 * TENANT SCOPING:
 * ---------------
 * All endpoints in this controller are authenticated (JWT required). The TenantFilter
 * extracts the tenant_id from the JWT attributes and sets TenantContext before the
 * request reaches this controller. This means every database query triggered by
 * ClientService is automatically scoped to the correct tenant schema -- a company
 * admin can only see clients that belong to their own tenant.
 *
 * NOTE: Client registration is NOT in this controller. It lives in a separate
 * public AuthController because it is unauthenticated (no JWT yet).
 *
 * SECURITY:
 * ---------
 * - @PreAuthorize(RoleConstants.HAS_ADMIN_OR_ROLES) = hasAnyRole('ADMIN', 'ROLE1', 'ROLE2')
 *   restricts listing and detail endpoints to company staff.
 * - @PreAuthorize(RoleConstants.HAS_ADMIN) = hasRole('ADMIN') restricts updates to admins.
 * - The /profile endpoint has no @PreAuthorize, relying on the global security config
 *   which requires authentication for all /api/v1/clients/** paths.
 *
 * RESPONSE FORMAT:
 * ----------------
 * All responses are wrapped in ApiResponse<T> for consistent JSON structure:
 *   { "success": true, "data": {...}, "message": "..." }
 * Paginated responses use PagedResponse<T> which includes content, page number,
 * total elements, etc.
 */
@RestController
@RequestMapping(AppConstants.CLIENT_PATH)
@RequiredArgsConstructor
@Tag(name = "Client Portal", description = "Client profile and management endpoints")
public class ClientController {

    private final ClientService clientService;

    /**
     * GET /api/v1/clients/profile
     *
     * Returns the authenticated client's own profile.
     *
     * The @AuthenticationPrincipal Jwt injection extracts the decoded JWT from the
     * SecurityContext. jwt.getSubject() returns the Keycloak user ID (the "sub" claim),
     * which is then used to look up the Client record in the current tenant's schema.
     *
     * No @PreAuthorize needed -- any authenticated user with a valid JWT can hit this
     * endpoint. In practice, only CLIENT-role users will call this (company staff use
     * the CompanyUser profile endpoint instead).
     */
    @GetMapping("/profile")
    @Operation(summary = "Get current client's profile")
    public ResponseEntity<ApiResponse<ClientDto>> getProfile(
            @AuthenticationPrincipal Jwt jwt) {
        ClientDto profile = clientService.getProfileByKeycloakId(jwt.getSubject());
        return ResponseEntity.ok(ApiResponse.success(profile));
    }

    /**
     * GET /api/v1/clients
     *
     * Lists all clients in the current tenant's schema with pagination.
     *
     * Restricted to company staff (ADMIN, ROLE1, ROLE2) via @PreAuthorize.
     * Clients themselves cannot list other clients -- they can only view their own
     * profile via /profile.
     *
     * @PageableDefault(size = 20) sets the default page size if the caller does not
     * provide ?size=N. Callers can also pass ?page=0&size=10&sort=firstName,asc.
     *
     * The result is wrapped in PagedResponse which includes pagination metadata
     * (totalElements, totalPages, currentPage) alongside the content list.
     */
    @GetMapping
    @PreAuthorize(RoleConstants.HAS_ADMIN_OR_ROLES)
    @Operation(summary = "List all clients (Company ADMIN/ROLE1/ROLE2 only)")
    public ResponseEntity<ApiResponse<PagedResponse<ClientDto>>> listClients(
            @PageableDefault(size = 20) Pageable pageable) {
        Page<ClientDto> clients = clientService.listClients(pageable);
        return ResponseEntity.ok(ApiResponse.success(PagedResponse.from(clients)));
    }

    /**
     * GET /api/v1/clients/{clientId}
     *
     * Retrieves a single client's details by their database UUID.
     *
     * Restricted to company staff (ADMIN, ROLE1, ROLE2). The clientId is a path
     * variable UUID that Spring automatically parses. If the UUID is malformed,
     * Spring returns a 400 Bad Request. If no client exists with that ID in the
     * tenant's schema, ClientService throws ResourceNotFoundException (404).
     */
    @GetMapping("/{clientId}")
    @PreAuthorize(RoleConstants.HAS_ADMIN_OR_ROLES)
    @Operation(summary = "Get client by ID (Company ADMIN/ROLE1/ROLE2 only)")
    public ResponseEntity<ApiResponse<ClientDto>> getClient(@PathVariable UUID clientId) {
        return ResponseEntity.ok(ApiResponse.success(clientService.getClientById(clientId)));
    }

    /**
     * PUT /api/v1/clients/{clientId}
     *
     * Updates a client's mutable fields (firstName, lastName, phone, metadata).
     *
     * Restricted to ADMIN only -- more restrictive than the read endpoints because
     * modifying client data is a sensitive operation. The request body is a ClientDto
     * where only non-null fields are applied (partial update pattern).
     *
     * The success response includes a confirmation message ("Client updated successfully")
     * in addition to the updated client data.
     */
    @PutMapping("/{clientId}")
    @PreAuthorize(RoleConstants.HAS_ADMIN)
    @Operation(summary = "Update client details (ADMIN only)")
    public ResponseEntity<ApiResponse<ClientDto>> updateClient(
            @PathVariable UUID clientId,
            @RequestBody ClientDto updateDto) {
        ClientDto updated = clientService.updateClient(clientId, updateDto);
        return ResponseEntity.ok(ApiResponse.success(updated, "Client updated successfully"));
    }
}
