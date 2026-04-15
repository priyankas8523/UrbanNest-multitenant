package com.keycloak.demo.common.exception;

/**
 * Generic "not found" exception for any entity lookup that returns empty.
 *
 * Triggers: 404 NOT_FOUND response via GlobalExceptionHandler.
 *
 * Usage pattern in services:
 *   companyUserRepository.findById(userId)
 *       .orElseThrow(() -> new ResourceNotFoundException("CompanyUser", "id", userId));
 *
 * This produces a clear message like: "CompanyUser not found with id: 550e8400-..."
 */
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String resource, String field, Object value) {
        super(String.format("%s not found with %s: %s", resource, field, value));
    }
}
