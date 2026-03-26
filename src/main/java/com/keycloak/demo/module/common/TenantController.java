package com.keycloak.demo.module.common;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/tenants")
public class TenantController {
    private final TenantService tenantService;

    @PostMapping
    public String createTenant(@RequestParam String name){
        String schemaName = "tenant_"+name;
        tenantService.createTenant(schemaName);
        return "Tenant Created Successfully" + schemaName;
    }

}
