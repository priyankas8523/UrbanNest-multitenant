package com.multitenant.app.module.subscription.service;

import com.multitenant.app.common.enums.TenantStatus;
import com.multitenant.app.common.exception.BusinessException;
import com.multitenant.app.common.exception.ResourceNotFoundException;
import com.multitenant.app.common.exception.TenantNotFoundException;
import com.multitenant.app.master.dto.TenantDto;
import com.multitenant.app.master.entity.SubscriptionPlan;
import com.multitenant.app.master.entity.Tenant;
import com.multitenant.app.master.repository.SubscriptionPlanRepository;
import com.multitenant.app.master.repository.TenantRepository;
import com.multitenant.app.master.service.TenantProvisioningService;
import com.multitenant.app.module.subscription.dto.PurchasePlanRequest;
import com.multitenant.app.module.subscription.dto.SubscriptionResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * SubscriptionService - Handles plan purchase and triggers schema provisioning.
 *
 * THE BILLING GATE:
 * This service is the bridge between Phase 1 (registration) and Phase 2 (provisioning).
 * When an ADMIN buys a plan, this service:
 *   1. Validates the plan exists and is active
 *   2. Assigns the plan to the tenant record
 *   3. Triggers TenantProvisioningService.provisionTenantSchema() to create the schema
 *   4. Returns confirmation with ACTIVE status
 *
 * After this, the admin can manage users, roles, clients in their isolated schema.
 *
 * FUTURE: Integrate with Stripe/Razorpay for actual payment processing before step 2.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private final TenantRepository tenantRepository;
    private final SubscriptionPlanRepository planRepository;
    private final TenantProvisioningService provisioningService;

    /**
     * Purchase a subscription plan for the authenticated tenant.
     *
     * FLOW:
     *   1. Look up tenant by tenantId (from JWT)
     *   2. Verify tenant is in REGISTERED state (hasn't bought a plan yet)
     *   3. Look up and validate the plan
     *   4. Assign plan to tenant
     *   5. Trigger schema provisioning (creates schema, runs migrations, activates tenant)
     *   6. Return confirmation
     *
     * @param tenantId The tenant slug (extracted from JWT tenant_id claim)
     * @param request  Contains the plan name to purchase
     * @return SubscriptionResponse with confirmation details
     */
    @Transactional
    public SubscriptionResponse purchasePlan(String tenantId, PurchasePlanRequest request) {
        log.info("Tenant '{}' purchasing plan '{}'", tenantId, request.getPlanName());

        // Step 1: Find the tenant
        Tenant tenant = tenantRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new TenantNotFoundException(tenantId));

        // Step 2: Verify tenant is in REGISTERED state
        if (tenant.getStatus() != TenantStatus.REGISTERED) {
            throw new BusinessException(
                    "Cannot purchase plan. Tenant status is " + tenant.getStatus() +
                    ". Only REGISTERED tenants can purchase their first plan.");
        }

        // Step 3: Validate the plan exists and is active
        SubscriptionPlan plan = planRepository.findByName(request.getPlanName().toUpperCase())
                .orElseThrow(() -> new ResourceNotFoundException("SubscriptionPlan", "name", request.getPlanName()));
        if (!plan.isActive()) {
            throw new BusinessException("Plan '" + request.getPlanName() + "' is no longer available");
        }

        // Step 4: Assign plan to tenant
        tenant.setSubscriptionPlanId(plan.getId());
        tenantRepository.save(tenant);

        // Step 5: Trigger Phase 2 - schema creation + activation
        // This creates the PostgreSQL schema, runs migrations, creates CompanyUser record,
        // and transitions tenant from REGISTERED -> PROVISIONING -> ACTIVE
        TenantDto provisionedTenant = provisioningService.provisionTenantSchema(tenantId);

        log.info("Tenant '{}' successfully purchased plan '{}' and is now ACTIVE", tenantId, request.getPlanName());

        // Step 6: Return confirmation
        return SubscriptionResponse.builder()
                .tenantId(tenantId)
                .planName(plan.getName())
                .status(provisionedTenant.getStatus())
                .message("Plan purchased successfully. Your workspace is now active.")
                .build();
    }

    /** List all available subscription plans for display on the purchase page. */
    public List<SubscriptionPlan> getAvailablePlans() {
        return planRepository.findAll().stream()
                .filter(SubscriptionPlan::isActive)
                .toList();
    }
}
