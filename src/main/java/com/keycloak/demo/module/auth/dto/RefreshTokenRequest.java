package com.keycloak.demo.module.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * RefreshTokenRequest -- DTO used to request a new access token using an existing refresh token.
 *
 * This is used by both the Company Portal and Client Portal for two operations:
 *   1. Token refresh (/refresh): exchanges the refresh token for a fresh access_token +
 *      refresh_token pair via Keycloak's "refresh_token" grant type.
 *   2. Logout (/logout): sends the refresh token to Keycloak's logout endpoint so the
 *      server invalidates the session. After logout, the refresh token can no longer be used.
 *
 * ---- Refresh Token Flow (step by step) ----
 *
 * 1. The user logs in and receives an access_token (short-lived, e.g. 5 min) and a
 *    refresh_token (longer-lived, e.g. 30 min).
 * 2. The frontend uses the access_token for API calls. When it is about to expire (or
 *    after receiving a 401 Unauthorized), the frontend sends the refresh_token to the
 *    /refresh endpoint.
 * 3. The backend forwards the refresh_token to Keycloak's token endpoint with
 *    grant_type=refresh_token, along with the appropriate client_id and client_secret
 *    (company-portal or client-portal, depending on the controller).
 * 4. Keycloak validates the refresh_token. If valid, it returns a brand-new access_token
 *    AND a new refresh_token (Keycloak rotates refresh tokens by default for security).
 * 5. The old refresh_token is now invalid. The frontend must store and use the new one.
 * 6. If the refresh_token is expired or already revoked, Keycloak returns a 400 error,
 *    and the user must log in again with credentials.
 *
 * ---- Security Notes ----
 *
 * - Refresh tokens are single-use by default in Keycloak (token rotation). If a refresh
 *   token is replayed, Keycloak revokes the entire session as a precaution against theft.
 * - This DTO is also used for the /logout endpoint. Sending the refresh token to logout
 *   ensures Keycloak invalidates the server-side session, preventing the token from being
 *   reused even if it has not yet expired.
 *
 * Example JSON body:
 * <pre>
 * {
 *   "refreshToken": "eyJhbGciOiJIUzI1NiIs..."
 * }
 * </pre>
 */
@Data               // Lombok: generates getters, setters, toString, equals, hashCode
@NoArgsConstructor  // Required by Jackson for JSON deserialization
@AllArgsConstructor // Convenience constructor for tests and programmatic usage
public class RefreshTokenRequest {

    /**
     * The refresh token previously issued by Keycloak during login or a prior refresh.
     *
     * Validation:
     *  - @NotBlank: ensures the token is present and not empty. Without a valid refresh
     *    token, neither refresh nor logout can proceed.
     *
     * The token value is an opaque or JWT string depending on Keycloak configuration.
     * This application treats it as an opaque string and passes it directly to Keycloak.
     */
    @NotBlank(message = "Refresh token is required")
    private String refreshToken;
}
