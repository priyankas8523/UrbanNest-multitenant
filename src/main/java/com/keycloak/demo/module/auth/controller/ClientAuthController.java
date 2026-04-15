package com.keycloak.demo.module.auth.controller;

import com.keycloak.demo.common.constants.AppConstants;
import com.keycloak.demo.common.dto.ApiResponse;
import com.keycloak.demo.common.exception.TenantResolutionException;
import com.keycloak.demo.iam.IamService;
import com.keycloak.demo.module.auth.dto.ClientRegistrationRequest;
import com.keycloak.demo.module.auth.dto.LoginRequest;
import com.keycloak.demo.module.auth.dto.LoginResponse;
import com.keycloak.demo.module.auth.dto.RefreshTokenRequest;
import com.keycloak.demo.module.client.dto.ClientDto;
import com.keycloak.demo.module.client.service.ClientService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * ClientAuthController - Registration, login, refresh, logout for the Client Portal.
 *
 * Clients are end-users of a specific company (tenant). Registration requires
 * X-Tenant-ID header to identify which company the client registers with.
 *
 * Uses IamService (unified facade) instead of separate Keycloak*Service classes.
 * Uses client-portal Keycloak client (separate from company-portal).
 */
@RestController
@RequestMapping(AppConstants.CLIENT_AUTH_PATH)
@RequiredArgsConstructor
@Tag(name = "Client Authentication", description = "Client portal registration, login, and token management")
public class ClientAuthController {

    private final ClientService clientService;
    private final IamService iamService;

    @Value("${keycloak.app.client-portal-client-id}")
    private String clientPortalClientId;

    @Value("${keycloak.app.client-portal-client-secret}")
    private String clientPortalClientSecret;

    /**
     * POST /register - Register as a client for a specific company.
     * Requires X-Tenant-ID header. Only works if the tenant is ACTIVE (has a provisioned schema).
     */
    @PostMapping("/register")
    @Operation(summary = "Register as a client for a specific company/tenant")
    public ResponseEntity<ApiResponse<ClientDto>> register(
            @Parameter(description = "Tenant ID of the company to register with")
            @RequestHeader(AppConstants.TENANT_HEADER) String tenantId,
            @Valid @RequestBody ClientRegistrationRequest request) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new TenantResolutionException("X-Tenant-ID header is required for client registration");
        }
        ClientDto client = clientService.registerClient(tenantId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(client, "Client registered successfully"));
    }

    /** POST /login - Authenticate client via Keycloak. Returns JWT tokens. */
    @PostMapping("/login")
    @Operation(summary = "Login as a client")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @Valid @RequestBody LoginRequest request) {
        LoginResponse response = iamService.login(
                request.getEmail(), request.getPassword(),
                clientPortalClientId, clientPortalClientSecret);
        return ResponseEntity.ok(ApiResponse.success(response, "Login successful"));
    }

    /** POST /refresh - Exchange refresh token for new token pair. */
    @PostMapping("/refresh")
    @Operation(summary = "Refresh access token")
    public ResponseEntity<ApiResponse<LoginResponse>> refresh(
            @Valid @RequestBody RefreshTokenRequest request) {
        LoginResponse response = iamService.refreshToken(
                request.getRefreshToken(), clientPortalClientId, clientPortalClientSecret);
        return ResponseEntity.ok(ApiResponse.success(response, "Token refreshed"));
    }

    /** POST /logout - Invalidate refresh token and end Keycloak session. */
    @PostMapping("/logout")
    @Operation(summary = "Logout (revoke refresh token)")
    public ResponseEntity<ApiResponse<Void>> logout(
            @Valid @RequestBody RefreshTokenRequest request) {
        iamService.logout(request.getRefreshToken(), clientPortalClientId, clientPortalClientSecret);
        return ResponseEntity.ok(ApiResponse.success(null, "Logged out successfully"));
    }
}
