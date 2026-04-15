package com.multitenant.app.module.auth.controller;

import com.multitenant.app.common.constants.AppConstants;
import com.multitenant.app.common.dto.ApiResponse;
import com.multitenant.app.iam.IamService;
import com.multitenant.app.master.dto.TenantDto;
import com.multitenant.app.master.dto.TenantRegistrationRequest;
import com.multitenant.app.master.service.TenantProvisioningService;
import com.multitenant.app.module.auth.dto.LoginRequest;
import com.multitenant.app.module.auth.dto.LoginResponse;
import com.multitenant.app.module.auth.dto.RefreshTokenRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * CompanyAuthController - Registration, login, refresh, and logout for the Company Portal.
 *
 * REGISTRATION FLOW (two-phase):
 *   POST /register -> Phase 1 ONLY: Keycloak user + public schema record (status = REGISTERED)
 *   NO schema is created during registration. Admin must buy a plan first via SubscriptionController.
 *   This keeps registration fast (~2s) and avoids wasted schemas.
 *
 * After registration, admin can login immediately but has limited access until they purchase a plan.
 *
 * Uses IamService (unified facade) instead of separate Keycloak*Service classes.
 * Uses company-portal Keycloak client for token operations (separate from client-portal).
 */
@RestController
@RequestMapping(AppConstants.COMPANY_AUTH_PATH)
@RequiredArgsConstructor
@Tag(name = "Company Authentication", description = "Company portal registration, login, and token management")
public class CompanyAuthController {

    private final TenantProvisioningService provisioningService;  // Phase 1 registration
    private final IamService iamService;                          // Unified IAM facade

    @Value("${keycloak.app.company-portal-client-id}")
    private String companyClientId;

    @Value("${keycloak.app.company-portal-client-secret}")
    private String companyClientSecret;

    /**
     * POST /register - Phase 1: Lightweight company registration.
     * Creates Keycloak user + group + public.tenants record (REGISTERED status).
     * Does NOT create a database schema. Admin must purchase a plan to get a schema.
     */
    @PostMapping("/register")
    @Operation(summary = "Register a new company (Phase 1: Keycloak + public record only, no schema)")
    public ResponseEntity<ApiResponse<TenantDto>> register(
            @Valid @RequestBody TenantRegistrationRequest request) {
        TenantDto tenant = provisioningService.registerTenant(request);  // Phase 1 only
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(tenant, "Company registered. Please purchase a plan to activate your workspace."));
    }

    /** POST /login - Authenticate company user via Keycloak. Returns JWT tokens. */
    @PostMapping("/login")
    @Operation(summary = "Login as a company user")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @Valid @RequestBody LoginRequest request) {
        LoginResponse response = iamService.login(
                request.getEmail(), request.getPassword(),
                companyClientId, companyClientSecret);
        return ResponseEntity.ok(ApiResponse.success(response, "Login successful"));
    }

    /** POST /refresh - Exchange refresh token for new token pair. */
    @PostMapping("/refresh")
    @Operation(summary = "Refresh access token")
    public ResponseEntity<ApiResponse<LoginResponse>> refresh(
            @Valid @RequestBody RefreshTokenRequest request) {
        LoginResponse response = iamService.refreshToken(
                request.getRefreshToken(), companyClientId, companyClientSecret);
        return ResponseEntity.ok(ApiResponse.success(response, "Token refreshed"));
    }

    /** POST /logout - Invalidate refresh token and end Keycloak session. */
    @PostMapping("/logout")
    @Operation(summary = "Logout (revoke refresh token)")
    public ResponseEntity<ApiResponse<Void>> logout(
            @Valid @RequestBody RefreshTokenRequest request) {
        iamService.logout(request.getRefreshToken(), companyClientId, companyClientSecret);
        return ResponseEntity.ok(ApiResponse.success(null, "Logged out successfully"));
    }
}
