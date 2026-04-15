package com.keycloak.demo.module.subscription.controller;

import com.keycloak.demo.common.constants.AppConstants;
import com.keycloak.demo.common.constants.RoleConstants;
import com.keycloak.demo.common.dto.ApiResponse;
import com.keycloak.demo.master.entity.SubscriptionPlan;
import com.keycloak.demo.module.subscription.dto.PurchasePlanRequest;
import com.keycloak.demo.module.subscription.dto.SubscriptionResponse;
import com.keycloak.demo.module.subscription.service.SubscriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * SubscriptionController - Handles subscription plan purchase and listing.
 *
 * FLOW:
 *   1. Admin registers company -> status = REGISTERED (no schema)
 *   2. Admin logs in -> sees limited dashboard with "Buy Plan" option
 *   3. GET /subscriptions/plans -> shows available plans (FREE, STARTER, etc.)
 *   4. POST /subscriptions/purchase -> buys a plan -> triggers schema creation
 *   5. Tenant becomes ACTIVE -> admin redirected to full dashboard
 *
 * SECURITY:
 *   - GET /plans is public (anyone can view available plans before login)
 *   - POST /purchase requires ADMIN role (only the company admin can buy)
 *   - Tenant ID is extracted from the JWT's tenant_id claim
 */
@RestController
@RequestMapping(AppConstants.SUBSCRIPTION_PATH)
@RequiredArgsConstructor
@Tag(name = "Subscription Management", description = "Plan purchase and subscription management")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    /**
     * GET /v1/subscriptions/plans - List all available subscription plans.
     * Public endpoint (no auth required) so companies can see pricing before registering.
     */
    @GetMapping("/plans")
    @Operation(summary = "List available subscription plans")
    public ResponseEntity<ApiResponse<List<SubscriptionPlan>>> getPlans() {
        return ResponseEntity.ok(ApiResponse.success(subscriptionService.getAvailablePlans()));
    }

    /**
     * POST /v1/subscriptions/purchase - Purchase a plan and activate the tenant.
     *
     * Requires ADMIN role. Extracts tenant_id from JWT to identify which tenant is purchasing.
     * This is the key transition: REGISTERED -> PROVISIONING -> ACTIVE.
     * After this call succeeds, the admin has a fully provisioned workspace.
     */
    @PostMapping("/purchase")
    @PreAuthorize(RoleConstants.HAS_ADMIN)
    @Operation(summary = "Purchase a subscription plan (triggers schema provisioning)")
    public ResponseEntity<ApiResponse<SubscriptionResponse>> purchasePlan(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody PurchasePlanRequest request) {
        // Extract tenant_id from JWT claim (set during Keycloak user creation)
        String tenantId = jwt.getClaimAsString("tenant_id");
        SubscriptionResponse response = subscriptionService.purchasePlan(tenantId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Subscription activated successfully"));
    }
}
