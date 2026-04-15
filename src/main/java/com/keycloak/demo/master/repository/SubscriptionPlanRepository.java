package com.keycloak.demo.master.repository;

import com.keycloak.demo.master.entity.SubscriptionPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for the public.subscription_plans table.
 *
 * Provides access to the globally shared subscription plan definitions.
 * Like TenantRepository, this always operates on the public schema regardless of
 * which tenant is currently active in the request context.
 *
 * Used when:
 *   - Assigning a plan to a tenant during or after provisioning.
 *   - Checking plan limits (maxUsers, maxClients) before allowing resource creation.
 *   - Displaying available plans in an admin or self-service UI.
 */
@Repository
public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlan, UUID> {

    /**
     * Find a subscription plan by its display name (e.g., "Professional").
     * Useful for seeding/migration scripts and admin APIs where the plan is referenced
     * by name rather than UUID. Returns Optional because the name may not match any plan.
     */
    Optional<SubscriptionPlan> findByName(String name);
}
