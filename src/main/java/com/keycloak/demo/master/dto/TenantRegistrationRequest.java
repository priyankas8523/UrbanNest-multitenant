package com.keycloak.demo.master.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Input DTO for new company (tenant) registration.
 *
 * This is what the registration endpoint receives when a new company wants to sign up.
 * It contains everything needed to:
 *   1. Create the tenant record in the public schema (companyName).
 *   2. Create the admin user in Keycloak (adminEmail, password, firstName, lastName).
 *   3. Create the initial CompanyUser record in the tenant's own schema.
 *
 * Jakarta Bean Validation annotations ensure the request is validated BEFORE it reaches
 * the service layer. Spring's @Valid annotation on the controller parameter triggers this.
 * If validation fails, Spring returns a 400 Bad Request with the error messages defined below,
 * and the provisioning service is never called.
 *
 * Note: This DTO does NOT include a plan selection. The plan can be assigned separately
 * after registration, or a default plan can be auto-assigned during provisioning.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantRegistrationRequest {

    /**
     * The company's display name (e.g., "Acme Corporation").
     * This gets slugified into the tenantId (e.g., "acme-corporation") and also used
     * to derive the PostgreSQL schema name (e.g., "tenant_acme_corporation").
     * Must be between 2-255 chars to avoid empty slugs or absurdly long schema names.
     */
    @NotBlank(message = "Company name is required")
    @Size(min = 2, max = 255, message = "Company name must be between 2 and 255 characters")
    private String companyName;

    /**
     * Email address for the first admin user of this company.
     * This becomes the owner_email in the Tenant record AND the login credential in Keycloak.
     * Must be a valid email format -- Keycloak will also validate this, but we catch it early.
     * Must be unique across all tenants (one email = one company ownership).
     */
    @NotBlank(message = "Admin email is required")
    @Email(message = "Invalid email format")
    private String adminEmail;

    /**
     * Password for the initial admin user's Keycloak account.
     * Sent to Keycloak during user creation. Minimum 8 characters enforced here;
     * Keycloak may enforce additional password policies (uppercase, special chars, etc.)
     * depending on realm configuration.
     * IMPORTANT: This field is only used during provisioning and is never stored in our database.
     */
    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    private String password;

    /**
     * Admin user's first name. Stored in both Keycloak (for the user profile)
     * and in the CompanyUser record in the tenant's schema.
     */
    @NotBlank(message = "First name is required")
    private String firstName;

    /**
     * Admin user's last name. Same usage as firstName.
     */
    @NotBlank(message = "Last name is required")
    private String lastName;

    /**
     * Optional phone number for the admin user. Not validated for format here because
     * phone formats vary globally. Stored in the CompanyUser record for contact purposes.
     * Nullable -- no @NotBlank annotation.
     */
    private String phone;
}
