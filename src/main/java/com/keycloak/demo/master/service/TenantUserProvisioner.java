package com.multitenant.app.master.service;

import com.multitenant.app.module.company.entity.CompanyUser;
import com.multitenant.app.module.company.repository.CompanyUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists the initial CompanyUser record inside a tenant's newly created schema.
 *
 * WHY THIS IS A SEPARATE BEAN (not a private method in TenantProvisioningService):
 *
 * TenantProvisioningService.provisionTenantSchema() runs in a @Transactional context
 * that was opened with search_path=public (the default). Hibernate binds one JDBC
 * connection to that transaction for its entire lifetime. Calling TenantContext.setTenantId()
 * mid-transaction has no effect on the already-bound connection -- Hibernate only consults
 * CurrentTenantIdentifierResolver when OPENING a new transaction/session.
 *
 * Solution: Delegate the save to THIS bean with Propagation.REQUIRES_NEW. Spring's AOP
 * proxy intercepts the cross-bean call, suspends the outer transaction, and starts a fresh
 * one. When Hibernate opens a connection for the new transaction it asks
 * CurrentTenantIdentifierResolverImpl -> TenantContext.getTenantId() -> "acme_corp"
 * -> SchemaMultiTenantConnectionProvider sets search_path=tenant_acme_corp -> the INSERT
 * lands in the correct schema.
 *
 * Self-invocation (calling this.saveAdminUser() from within TenantProvisioningService)
 * would bypass the AOP proxy entirely, which is why a separate bean is required.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantUserProvisioner {

    private final CompanyUserRepository companyUserRepository;

    /**
     * Saves the initial admin CompanyUser in the tenant's schema.
     *
     * Must be called AFTER TenantContext.setTenantId() is set on the current thread
     * so that Hibernate routes the INSERT to the correct tenant schema.
     *
     * Propagation.REQUIRES_NEW suspends any outer transaction and opens a fresh one,
     * guaranteeing a new JDBC connection with search_path set to the tenant schema.
     *
     * @param companyUser The admin user to persist in the tenant schema
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveAdminUser(CompanyUser companyUser) {
        companyUserRepository.save(companyUser);
        log.info("Admin CompanyUser '{}' saved in tenant schema", companyUser.getEmail());
    }
}
