package com.multitenant.app.module.subscription.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for purchasing a subscription plan.
 *
 * Sent by the company ADMIN after registration to buy a plan.
 * This triggers schema provisioning - the tenant's database schema is created
 * only AFTER a plan is purchased, not during registration.
 *
 * FLOW: Admin registers -> logs in -> sees "Buy Plan" -> sends this request
 *       -> schema created -> tenant becomes ACTIVE -> admin can manage everything
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PurchasePlanRequest {

    /** Name of the plan to purchase: "FREE", "STARTER", "PROFESSIONAL", "ENTERPRISE" */
    @NotBlank(message = "Plan name is required")
    private String planName;
}
