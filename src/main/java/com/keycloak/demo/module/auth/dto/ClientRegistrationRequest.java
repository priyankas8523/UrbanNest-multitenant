package com.keycloak.demo.module.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ClientRegistrationRequest -- DTO for registering a new end-user (client) on the Client Portal.
 *
 * "Client" here means an end-user / customer of a specific company (tenant), NOT an OAuth client.
 * For example, if "Acme Corp" is a tenant, then John Doe signing up to use Acme's services
 * would submit this registration request.
 *
 * ---- Tenant Context (X-Tenant-ID header) ----
 *
 * This DTO does NOT contain a tenantId field. Instead, the tenant is identified by the
 * mandatory X-Tenant-ID HTTP header that the frontend must include when calling the
 * client registration endpoint (POST /api/client/auth/register).
 *
 * Why a header instead of a body field?
 *  - The tenant ID is infrastructure-level routing information, not user-supplied data.
 *  - The Client Portal frontend already knows which tenant it belongs to (typically from
 *    the subdomain or app configuration) and sets the header automatically.
 *  - Keeping tenant resolution in the header makes it consistent with all other
 *    tenant-scoped API calls in the system.
 *
 * The controller extracts the X-Tenant-ID header and passes it to the ClientService,
 * which then:
 *  1. Resolves the tenant's Keycloak realm and database schema.
 *  2. Creates the user in Keycloak under that tenant's realm.
 *  3. Persists the client record in the tenant's database schema.
 *
 * ---- Difference from Company Registration ----
 *
 * Company registration (TenantRegistrationRequest) provisions an entire new tenant:
 * realm, database schema, admin user, etc. Client registration only creates a user
 * within an already-provisioned tenant. The two flows use different DTOs, controllers,
 * and Keycloak client IDs (company-portal vs. client-portal).
 *
 * Example JSON body (with required header X-Tenant-ID: acme-corp-uuid):
 * <pre>
 * {
 *   "email":     "john.doe@example.com",
 *   "password":  "myStr0ngP@ss",
 *   "firstName": "John",
 *   "lastName":  "Doe",
 *   "phone":     "+1-555-123-4567"
 * }
 * </pre>
 */
@Data               // Lombok: getters, setters, toString, equals, hashCode
@Builder            // Lombok: enables fluent builder pattern for service/test code
@NoArgsConstructor  // Required by Jackson for JSON deserialization
@AllArgsConstructor // Required by @Builder when @NoArgsConstructor is also present
public class ClientRegistrationRequest {

    /**
     * The client's email address, used as the Keycloak username.
     *
     * Validation:
     *  - @NotBlank: must not be null, empty, or whitespace-only.
     *  - @Email:    must conform to a valid email pattern.
     *
     * This email is used both as the login credential in Keycloak and as the primary
     * contact email in the client record stored in the tenant's database.
     */
    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    /**
     * The client's chosen password.
     *
     * Validation:
     *  - @NotBlank: must not be null, empty, or whitespace-only.
     *  - @Size(min = 8): enforces a minimum length of 8 characters at the application level.
     *    Keycloak may enforce additional password policies (uppercase, digits, special chars)
     *    configured per-realm.
     *
     * The password is sent to Keycloak during user creation and is never stored by this
     * application. Keycloak hashes and manages the credential internally.
     */
    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    private String password;

    /**
     * The client's first name.
     *
     * Validation:
     *  - @NotBlank: required for creating the Keycloak user profile and the local
     *    client record. Keycloak stores this as the "firstName" attribute.
     */
    @NotBlank(message = "First name is required")
    private String firstName;

    /**
     * The client's last name.
     *
     * Validation:
     *  - @NotBlank: required for creating the Keycloak user profile and the local
     *    client record. Keycloak stores this as the "lastName" attribute.
     */
    @NotBlank(message = "Last name is required")
    private String lastName;

    /**
     * The client's phone number (optional).
     *
     * No validation constraint -- this field may be null or empty. If provided, it is
     * stored in the local client record and optionally as a Keycloak user attribute.
     * The format is not enforced here; consider adding @Pattern validation if a specific
     * phone format is required.
     */
    private String phone;
}
