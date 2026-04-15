package com.keycloak.demo.module.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * LoginRequest -- DTO used for authenticating users on both the Company Portal and Client Portal.
 *
 * This single DTO serves both portals because the login contract is identical: the user supplies
 * an email and a password, and the backend proxies the credentials to Keycloak's token endpoint.
 * Which Keycloak client (company-portal vs. client-portal) is used depends on the controller
 * that receives this request -- see {@link com.multitenant.app.module.auth.controller.CompanyAuthController}
 * and {@link com.multitenant.app.module.auth.controller.ClientAuthController}.
 *
 * Validation is enforced at the controller layer via {@code @Valid}. If any constraint fails,
 * Spring returns a 400 Bad Request with the corresponding message before the service layer is hit.
 *
 * Example JSON body:
 * <pre>
 * {
 *   "email": "admin@acme.com",
 *   "password": "s3cur3Pa$$"
 * }
 * </pre>
 */
@Data               // Lombok: generates getters, setters, toString, equals, and hashCode
@NoArgsConstructor  // Lombok: generates a no-arg constructor (required by Jackson for deserialization)
@AllArgsConstructor // Lombok: generates an all-args constructor (convenient for tests and builders)
public class LoginRequest {

    /**
     * The user's email address, which also serves as the Keycloak username.
     *
     * Validation:
     *  - @NotBlank: rejects null, empty string, and whitespace-only values.
     *  - @Email:    ensures the value matches a standard email pattern (RFC 5322 simplified).
     *
     * This email is passed directly to Keycloak's "Resource Owner Password Credentials" grant
     * as the "username" parameter. Keycloak looks up the user by this email within the
     * configured realm.
     */
    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    /**
     * The user's plaintext password.
     *
     * Validation:
     *  - @NotBlank: rejects null, empty, or whitespace-only values.
     *
     * The password is forwarded to Keycloak over HTTPS as part of the token request.
     * This application never stores or hashes the password itself -- Keycloak handles
     * all credential storage and verification. The password is not logged or persisted
     * at any point in the request lifecycle.
     */
    @NotBlank(message = "Password is required")
    private String password;
}
