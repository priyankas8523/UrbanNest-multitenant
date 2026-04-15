package com.keycloak.demo.common.exception;

/**
 * Thrown when a business rule is violated (not a technical error, not a "not found").
 *
 * Triggers: 422 UNPROCESSABLE_ENTITY response via GlobalExceptionHandler.
 *
 * Common scenarios:
 * - "Email already registered as a tenant owner" (duplicate registration attempt)
 * - "Cannot suspend a deleted tenant" (invalid state transition)
 * - "User already has role: ADMIN" (duplicate role assignment)
 * - "Tenant provisioning failed" (saga compensation triggered)
 *
 * Use this for any case where the request is well-formed (valid JSON, correct types)
 * but violates a business rule. 422 tells the client "I understood your request,
 * but I can't process it because it breaks a rule."
 */
public class BusinessException extends RuntimeException {
    public BusinessException(String message) {
        super(message);
    }
}
