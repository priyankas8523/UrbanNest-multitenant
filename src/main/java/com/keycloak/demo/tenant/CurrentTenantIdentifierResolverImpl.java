package com.keycloak.demo.tenant;

import org.springframework.stereotype.Component;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;

/**
 * Request → which tenant schema should be used?
 */
@Component
public class CurrentTenantIdentifierResolverImpl implements CurrentTenantIdentifierResolver{
    @Override
    public String resolveCurrentTenantIdentifier() {

        String tenant = TenantContext.getTenant();

        if (tenant != null) {
            return tenant;
        }

        return "public";
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return true;
    }
}
