package com.keycloak.demo.common.enums;

/**
 * Lifecycle states of a tenant (company) in the system.
 *
 * TWO-PHASE ONBOARDING FLOW:
 *   REGISTERED   -> Company signed up, Keycloak user created, public record saved.
 *                   NO schema yet. Admin can login but can only buy a subscription.
 *   PROVISIONING -> Admin bought a plan. Schema creation + migration in progress.
 *   ACTIVE       -> Schema ready. Admin can manage users, roles, clients.
 *   SUSPENDED    -> SUPER_ADMIN blocked access. Data preserved.
 *   DELETED      -> Permanently removed. Terminal state.
 *
 * TRANSITION MAP:
 *   REGISTERED   -> PROVISIONING  (admin purchases a subscription plan)
 *   PROVISIONING -> ACTIVE        (schema created + migrated successfully)
 *   ACTIVE       -> SUSPENDED     (SUPER_ADMIN suspends tenant)
 *   SUSPENDED    -> ACTIVE        (SUPER_ADMIN re-activates)
 *   ACTIVE       -> DELETED       (permanent removal)
 */
public enum TenantStatus {
    REGISTERED,     // Phase 1 complete: signed up, no schema yet, must buy a plan
    PROVISIONING,   // Phase 2 in progress: schema being created after plan purchase
    ACTIVE,         // Fully operational: schema ready, admin can manage everything
    SUSPENDED,      // Temporarily disabled by SUPER_ADMIN
    DELETED         // Permanently removed (terminal state)
}
