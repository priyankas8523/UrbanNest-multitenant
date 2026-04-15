package com.keycloak.demo.common.enums;

/**
 * Identifies which portal a user belongs to.
 *
 * This value is stored as a Keycloak user attribute ("portal_type") and injected
 * into the JWT as a custom claim. It allows the backend to distinguish between
 * company employees and client users even though they live in the same Keycloak realm.
 *
 * COMPANY - Users who registered via the Company Portal (admins, employees)
 * CLIENT  - Users who registered via the Client Portal (end customers)
 */
public enum PortalType {
    COMPANY,
    CLIENT
}
