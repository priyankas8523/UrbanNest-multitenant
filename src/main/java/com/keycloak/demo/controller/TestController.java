package com.keycloak.demo.controller;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestController {

    @GetMapping("/hello")
    public String hello(Authentication authentication) {
        JwtAuthenticationToken jwtAuth = (JwtAuthenticationToken) authentication;

        String username = jwtAuth.getToken().getClaim("preferred_username");
        String tenantId = jwtAuth.getToken().getClaim("tenantId");

        return "User = " + username + " Tenant = " + tenantId;
    }
}