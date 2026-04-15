package com.multitenant.app.module.company.controller;

import com.multitenant.app.common.constants.AppConstants;
import com.multitenant.app.common.constants.RoleConstants;
import com.multitenant.app.common.dto.ApiResponse;
import com.multitenant.app.common.dto.PagedResponse;
import com.multitenant.app.module.company.dto.CompanyUserDto;
import com.multitenant.app.module.company.service.CompanyService;
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
 * REST controller for company portal operations: user profile and user management.
 *
 * Base path: defined by {@link AppConstants#COMPANY_PATH} (e.g., "/api/company").
 *
 * This controller exposes two categories of endpoints:
 *
 * 1. Self-service (any authenticated user):
 *    - GET /profile: Retrieve the current user's own company profile.
 *      Uses the JWT "sub" claim (Keycloak user ID) to look up the user,
 *      so the user does not need to know their internal UUID.
 *
 * 2. Admin-only (requires ADMIN role):
 *    - GET /users: List all company users with pagination.
 *    - GET /users/{userId}: Get a specific user by internal UUID.
 *    - PUT /users/{userId}/deactivate: Soft-delete a user (disables in both DB and Keycloak).
 *    - PUT /users/{userId}/activate: Reactivate a previously deactivated user.
 *
 * Access control:
 * - All endpoints require authentication (a valid JWT token).
 * - Admin endpoints are protected with @PreAuthorize(RoleConstants.HAS_ADMIN), which
 *   checks for the ADMIN role in the JWT claims. This role is mapped from Keycloak's
 *   realm roles by the Spring Security OAuth2 resource server configuration.
 *
 * Tenant scoping:
 * - The tenant is resolved from the incoming request (e.g., via a header or subdomain)
 *   before the request reaches this controller. Hibernate then routes all database
 *   queries to the correct tenant schema automatically.
 */
@RestController
@RequestMapping(AppConstants.COMPANY_PATH)
@RequiredArgsConstructor
@Tag(name = "Company Portal", description = "Company user and profile management")
public class CompanyController {

    private final CompanyService companyService;

    /**
     * Get the current authenticated user's company profile.
     *
     * The @AuthenticationPrincipal annotation injects the decoded JWT token.
     * jwt.getSubject() returns the "sub" claim, which is the Keycloak user ID.
     * This ID is used to look up the corresponding CompanyUser record in the
     * current tenant's schema.
     *
     * Any authenticated user can call this endpoint -- no role restriction is applied.
     *
     * @param jwt the authenticated user's JWT token, injected by Spring Security
     * @return the user's profile wrapped in a standardized ApiResponse
     */
    @GetMapping("/profile")
    @Operation(summary = "Get current user's company profile")
    public ResponseEntity<ApiResponse<CompanyUserDto>> getProfile(
            @AuthenticationPrincipal Jwt jwt) {
        CompanyUserDto profile = companyService.getProfileByKeycloakId(jwt.getSubject());
        return ResponseEntity.ok(ApiResponse.success(profile));
    }

    /**
     * List all company users in the current tenant with pagination.
     *
     * ADMIN-only endpoint. The @PageableDefault(size = 20) sets the default page
     * size if the client does not specify one. Clients can control pagination via
     * query parameters: ?page=0&size=10&sort=email,asc.
     *
     * Returns a PagedResponse wrapper that includes pagination metadata (total
     * elements, total pages, etc.) alongside the user data.
     *
     * @param pageable pagination and sorting parameters from query string
     * @return paginated list of company users wrapped in ApiResponse
     */
    @GetMapping("/users")
    @PreAuthorize(RoleConstants.HAS_ADMIN)
    @Operation(summary = "List all company users (ADMIN only)")
    public ResponseEntity<ApiResponse<PagedResponse<CompanyUserDto>>> listUsers(
            @PageableDefault(size = 20) Pageable pageable) {
        Page<CompanyUserDto> users = companyService.listUsers(pageable);
        return ResponseEntity.ok(ApiResponse.success(PagedResponse.from(users)));
    }

    /**
     * Get a specific company user by their internal UUID.
     *
     * ADMIN-only endpoint. Returns the full user profile including role names.
     *
     * @param userId the internal UUID of the company user
     * @return the user's data wrapped in ApiResponse
     */
    @GetMapping("/users/{userId}")
    @PreAuthorize(RoleConstants.HAS_ADMIN)
    @Operation(summary = "Get company user by ID (ADMIN only)")
    public ResponseEntity<ApiResponse<CompanyUserDto>> getUser(@PathVariable UUID userId) {
        return ResponseEntity.ok(ApiResponse.success(companyService.getUserById(userId)));
    }

    /**
     * Deactivate (soft-delete) a company user.
     *
     * ADMIN-only endpoint. This triggers a two-step process:
     * 1. Sets isActive = false in the tenant's local database.
     * 2. Disables the user in Keycloak, preventing them from obtaining new JWT tokens.
     *
     * Uses PUT (not DELETE) because this is a soft-delete -- the user record is
     * preserved and can be reactivated later via the /activate endpoint.
     *
     * @param userId the internal UUID of the user to deactivate
     * @return success message wrapped in ApiResponse
     */
    @PutMapping("/users/{userId}/deactivate")
    @PreAuthorize(RoleConstants.HAS_ADMIN)
    @Operation(summary = "Deactivate a company user (ADMIN only)")
    public ResponseEntity<ApiResponse<Void>> deactivateUser(@PathVariable UUID userId) {
        companyService.deactivateUser(userId);
        return ResponseEntity.ok(ApiResponse.success(null, "User deactivated successfully"));
    }

    /**
     * Reactivate a previously deactivated company user.
     *
     * ADMIN-only endpoint. Reverses the deactivation:
     * 1. Sets isActive = true in the tenant's local database.
     * 2. Re-enables the user in Keycloak, allowing them to authenticate again.
     *
     * @param userId the internal UUID of the user to activate
     * @return success message wrapped in ApiResponse
     */
    @PutMapping("/users/{userId}/activate")
    @PreAuthorize(RoleConstants.HAS_ADMIN)
    @Operation(summary = "Activate a company user (ADMIN only)")
    public ResponseEntity<ApiResponse<Void>> activateUser(@PathVariable UUID userId) {
        companyService.activateUser(userId);
        return ResponseEntity.ok(ApiResponse.success(null, "User activated successfully"));
    }
}
