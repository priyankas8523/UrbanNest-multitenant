package com.multitenant.app.keycloak;

import com.multitenant.app.common.exception.BusinessException;
import com.multitenant.app.module.auth.dto.LoginResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Service that acts as a proxy between the frontend and Keycloak's OpenID Connect token endpoint.
 *
 * Instead of having the frontend (SPA, mobile app) call Keycloak's token endpoint directly,
 * all authentication requests are routed through this backend service. This proxy pattern
 * provides several critical benefits:
 *
 * 1. CLIENT SECRET HIDING: Keycloak confidential clients require a client_secret to obtain tokens.
 *    If the frontend called Keycloak directly, the client_secret would need to be embedded in the
 *    frontend code (JavaScript bundle, mobile app binary), where it can be easily extracted by
 *    attackers. By proxying through the backend, the client_secret stays server-side and is never
 *    exposed to the client. The frontend only sends username/password (or refresh token) to our
 *    backend API -- the backend appends the secret before forwarding to Keycloak.
 *
 * 2. CENTRALIZED CONTROL: All authentication flows pass through our backend, allowing us to:
 *    - Add custom validation before authenticating (e.g., check if tenant is active)
 *    - Log and audit all login/logout events in one place
 *    - Rate-limit authentication attempts at the application level
 *    - Transform or enrich responses before returning them to the frontend
 *    - Switch identity providers in the future without changing the frontend
 *
 * 3. CORS SIMPLIFICATION: The frontend only needs to communicate with one origin (our backend).
 *    Without the proxy, the frontend would need CORS configured for both the backend AND Keycloak,
 *    which adds complexity and potential security misconfiguration.
 *
 * 4. ABSTRACTION: The frontend does not need to know about Keycloak-specific endpoints, grant types,
 *    or token formats. It simply calls POST /api/auth/login with credentials and gets back tokens.
 *    If we ever replace Keycloak with another IdP, only this service changes -- not the frontend.
 *
 * This service communicates with Keycloak via standard HTTP POST requests to the OpenID Connect
 * token endpoint (for login/refresh) and the logout endpoint (for session termination), using
 * application/x-www-form-urlencoded content type as required by the OAuth 2.0 specification.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KeycloakAuthService {

    /**
     * RestTemplate for making HTTP calls to Keycloak's token and logout endpoints.
     * Unlike the Keycloak admin client (used in other services), this uses plain HTTP
     * because we are calling the public-facing OpenID Connect endpoints, not the Admin API.
     */
    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Keycloak's OpenID Connect token endpoint URL.
     * Typically: http(s)://{keycloak-host}/realms/{realm}/protocol/openid-connect/token
     * This endpoint handles both login (grant_type=password) and token refresh (grant_type=refresh_token).
     */
    @Value("${keycloak.app.token-url}")
    private String tokenUrl;

    /**
     * Keycloak's OpenID Connect logout endpoint URL.
     * Typically: http(s)://{keycloak-host}/realms/{realm}/protocol/openid-connect/logout
     * This endpoint invalidates the user's refresh token and server-side session.
     */
    @Value("${keycloak.app.logout-url}")
    private String logoutUrl;

    /**
     * Authenticates a user by exchanging their credentials for tokens via Keycloak.
     *
     * This uses the OAuth 2.0 "Resource Owner Password Credentials" (ROPC) grant type
     * (grant_type=password). The user provides their username and password, and Keycloak
     * validates them and returns an access token, refresh token, and metadata.
     *
     * The ROPC grant is suitable here because:
     * - The application owns the login UI (it is not delegating to Keycloak's login page)
     * - The backend is a trusted first-party application
     * - The client_secret is kept server-side (never sent to the frontend)
     *
     * The returned access token (JWT) contains the user's identity, tenant_id, portal_type,
     * and realm roles as claims. The frontend stores these tokens and sends the access token
     * in the Authorization header for subsequent API requests.
     *
     * @param username     the user's login identifier (email address in this system)
     * @param password     the user's password
     * @param clientId     the Keycloak client ID for this application
     * @param clientSecret the Keycloak client secret (confidential client credential)
     * @return a LoginResponse containing access_token, refresh_token, expiry info, and scope
     * @throws BusinessException if credentials are invalid or Keycloak is unreachable
     */
    public LoginResponse login(String username, String password, String clientId, String clientSecret) {
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("grant_type", "password");
        formData.add("client_id", clientId);
        formData.add("client_secret", clientSecret);
        formData.add("username", username);
        formData.add("password", password);

        return requestToken(formData);
    }

    /**
     * Obtains a new access token using a refresh token, without requiring the user's password.
     *
     * This uses the OAuth 2.0 "refresh_token" grant type. When the frontend's access token expires
     * (typically after a few minutes), it calls this method with the longer-lived refresh token
     * to get a fresh access token without forcing the user to re-enter credentials.
     *
     * The new access token will contain updated claims (e.g., if the user's roles or attributes
     * changed since the last token was issued). Keycloak also issues a new refresh token, rotating
     * the old one for security (refresh token rotation prevents replay attacks if a token is leaked).
     *
     * @param refreshToken the refresh token from a previous login or refresh operation
     * @param clientId     the Keycloak client ID
     * @param clientSecret the Keycloak client secret
     * @return a LoginResponse with new access_token, new refresh_token, and updated metadata
     * @throws BusinessException if the refresh token is expired/invalid or Keycloak is unreachable
     */
    public LoginResponse refreshToken(String refreshToken, String clientId, String clientSecret) {
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("grant_type", "refresh_token");
        formData.add("client_id", clientId);
        formData.add("client_secret", clientSecret);
        formData.add("refresh_token", refreshToken);

        return requestToken(formData);
    }

    /**
     * Logs out a user by invalidating their refresh token and Keycloak server-side session.
     *
     * This calls Keycloak's OpenID Connect logout endpoint, which:
     * - Invalidates the provided refresh token so it can no longer be used to obtain new access tokens
     * - Terminates the user's server-side session in Keycloak
     *
     * Note: The user's current access token remains valid until it expires (JWTs are stateless
     * and cannot be revoked without additional infrastructure like a token blacklist). However,
     * since access tokens are short-lived (typically 5-15 minutes), this is generally acceptable.
     * The refresh token invalidation is the critical part -- it prevents the user from silently
     * obtaining new access tokens.
     *
     * The frontend should discard both tokens from local storage after calling this endpoint.
     *
     * @param refreshToken the refresh token to invalidate
     * @param clientId     the Keycloak client ID
     * @param clientSecret the Keycloak client secret
     * @throws BusinessException if the logout request fails
     */
    public void logout(String refreshToken, String clientId, String clientSecret) {
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("client_id", clientId);
        formData.add("client_secret", clientSecret);
        formData.add("refresh_token", refreshToken);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(formData, headers);

        try {
            restTemplate.postForEntity(logoutUrl, request, Void.class);
            log.info("User logged out successfully");
        } catch (HttpClientErrorException e) {
            log.error("Logout failed: {}", e.getMessage());
            throw new BusinessException("Logout failed");
        }
    }

    /**
     * Internal helper that sends a form-encoded POST request to Keycloak's token endpoint
     * and maps the JSON response to a LoginResponse DTO.
     *
     * This method is shared by both login() and refreshToken() since they both call the same
     * Keycloak endpoint (the token URL) with different grant_type parameters but receive the
     * same response structure.
     *
     * Keycloak's token endpoint returns a JSON object with these fields (among others):
     * - access_token:  the JWT that the frontend sends in the Authorization header
     * - refresh_token: a longer-lived token used to obtain new access tokens
     * - token_type:    always "Bearer" for OAuth 2.0
     * - expires_in:    access token lifetime in seconds (e.g., 300 for 5 minutes)
     * - refresh_expires_in: refresh token lifetime in seconds (e.g., 1800 for 30 minutes)
     * - scope:         the OAuth scopes granted (e.g., "openid profile email")
     *
     * The @SuppressWarnings("unchecked") is needed because RestTemplate.postForEntity returns
     * a raw Map type when we pass Map.class, and we cast it to Map<String, Object>.
     *
     * @param formData the form parameters to send (grant_type, client_id, client_secret, plus
     *                 either username/password or refresh_token depending on the operation)
     * @return a LoginResponse DTO containing the parsed token response
     * @throws BusinessException if authentication fails (invalid credentials returns 401,
     *                           other errors are logged and wrapped)
     */
    @SuppressWarnings("unchecked")
    private LoginResponse requestToken(MultiValueMap<String, String> formData) {
        HttpHeaders headers = new HttpHeaders();
        // OAuth 2.0 specification requires token requests to use form-urlencoded content type
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(formData, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(tokenUrl, request, Map.class);
            Map<String, Object> body = response.getBody();

            if (body == null) {
                throw new BusinessException("Empty response from Keycloak");
            }

            // Map Keycloak's JSON response fields to our application's LoginResponse DTO.
            // This abstraction means the frontend never sees raw Keycloak response format --
            // if we switch identity providers, only this mapping needs to change.
            return LoginResponse.builder()
                    .accessToken((String) body.get("access_token"))
                    .refreshToken((String) body.get("refresh_token"))
                    .tokenType((String) body.get("token_type"))
                    .expiresIn((Integer) body.get("expires_in"))
                    .refreshExpiresIn((Integer) body.get("refresh_expires_in"))
                    .scope((String) body.get("scope"))
                    .build();

        } catch (HttpClientErrorException e) {
            // HTTP 401 specifically means invalid credentials (wrong username/password or
            // expired refresh token). We throw a clear message for this common case.
            if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                throw new BusinessException("Invalid credentials");
            }
            // Other HTTP errors (400 bad request, 403 forbidden, 500 server error, etc.)
            // are logged with details and wrapped in a generic error message.
            log.error("Token request failed: {}", e.getMessage());
            throw new BusinessException("Authentication failed: " + e.getMessage());
        }
    }
}
