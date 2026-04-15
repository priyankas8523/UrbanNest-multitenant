package com.keycloak.demo.module.subscription.dto;

import com.keycloak.demo.common.enums.TenantStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO returned after a successful plan purchase.
 * Confirms the plan name, tenant status (should be ACTIVE), and schema name.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionResponse {
    private String tenantId;          // Tenant slug
    private String planName;          // Plan purchased
    private TenantStatus status;      // Should be ACTIVE after purchase
    private String message;           // Human-readable confirmation
}
