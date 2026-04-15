package com.keycloak.demo.module.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * LoginResponse -- DTO that wraps the OAuth 2.0 token set returned by Keycloak after a
 * successful authentication (login) or token refresh.
 *
 * This object is built from Keycloak's POST /token endpoint response and forwarded to
 * the frontend. It contains everything the frontend needs to:
 *   1. Authenticate subsequent API calls (access_token).
 *   2. Silently renew the session when the access token expires (refresh_token).
 *   3. Know when to trigger a refresh (expires_in / refresh_expires_in).
 *
 * The @JsonProperty annotations ensure the JSON keys use Keycloak's snake_case naming
 * convention so the response is consistent with standard OAuth 2.0 token responses,
 * while the Java fields follow camelCase convention.
 *
 * Example JSON response:
 * <pre>
 * {
 *   "access_token":        "eyJhbGciOiJSUzI1NiIs...",
 *   "refresh_token":       "eyJhbGciOiJIUzI1NiIs...",
 *   "token_type":          "Bearer",
 *   "expires_in":          300,
 *   "refresh_expires_in":  1800,
 *   "scope":               "openid profile email"
 * }
 * </pre>
 */
@Data               // Lombok: getters, setters, toString, equals, hashCode
@Builder            // Lombok: enables the builder pattern for constructing instances in service code
@NoArgsConstructor  // Required by Jackson for deserialization from Keycloak's JSON response
@AllArgsConstructor // Required by @Builder to compile alongside @NoArgsConstructor
public class LoginResponse {

    /**
     * The JWT access token issued by Keycloak.
     *
     * Frontend usage:
     *  - Attach this token to every API request in the Authorization header:
     *    Authorization: Bearer <access_token>
     *  - The token is a signed JWT containing claims such as the user's email, roles,
     *    realm, and tenant ID. The backend validates this token on every request via
     *    Spring Security's resource server configuration.
     *  - This token is short-lived (see {@link #expiresIn}). Do NOT store it in
     *    localStorage if XSS is a concern; prefer httpOnly cookies or in-memory storage.
     */
    @JsonProperty("access_token")
    private String accessToken;

    /**
     * The refresh token issued by Keycloak.
     *
     * Frontend usage:
     *  - When the access_token expires (or shortly before), send this refresh_token to
     *    the /refresh endpoint to obtain a new access_token + refresh_token pair without
     *    requiring the user to re-enter credentials.
     *  - Store securely -- anyone with this token can obtain new access tokens.
     *  - On logout, send this token to the /logout endpoint so Keycloak invalidates it
     *    server-side. Simply discarding it client-side is not enough.
     */
    @JsonProperty("refresh_token")
    private String refreshToken;

    /**
     * The token type, which is always "Bearer" for Keycloak OAuth 2.0 flows.
     *
     * Frontend usage:
     *  - Use this as the prefix in the Authorization header: "Bearer <access_token>".
     *  - In practice this is always "Bearer", but using the returned value is more correct.
     */
    @JsonProperty("token_type")
    private String tokenType;

    /**
     * The number of seconds until the access_token expires, counted from the moment
     * Keycloak issued it (not from when the frontend received it).
     *
     * Typical value: 300 (5 minutes), configured in Keycloak realm/client settings.
     *
     * Frontend usage:
     *  - Start a timer or schedule a refresh call before this window elapses.
     *  - Example: if expiresIn = 300, schedule a refresh at ~270 seconds to avoid
     *    making API calls with an expired token.
     */
    @JsonProperty("expires_in")
    private int expiresIn;

    /**
     * The number of seconds until the refresh_token expires.
     *
     * Typical value: 1800 (30 minutes), configured in Keycloak realm/client settings.
     *
     * Frontend usage:
     *  - If the refresh token itself has expired, the user must log in again with
     *    credentials. There is no way to silently renew after this window closes.
     *  - This effectively defines the maximum idle session duration.
     */
    @JsonProperty("refresh_expires_in")
    private int refreshExpiresIn;

    /**
     * The OAuth 2.0 scopes granted for this token.
     *
     * Typical value: "openid profile email" -- meaning the access token's JWT payload
     * includes identity claims (sub, email, name, etc.).
     *
     * Frontend usage:
     *  - Generally informational. The frontend does not need to act on this directly,
     *    but it can be useful for debugging or conditionally showing UI elements based
     *    on granted scopes.
     */
    private String scope;
}
