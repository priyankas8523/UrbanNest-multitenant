package com.keycloak.demo.dto;

import java.util.List;

public class Literals {

    private Literals() {

    }

    public static final String EX_403 = "AccessForbidden";
    public static final String EX_404 = "ResourceNotFoundException";
    public static final String EX_409 = "DuplicateResourceException";
    public static final String EX_400 = "InvalidOperationException";
    public static final String EX_401 = "UnauthorizedException";
    public static final String EX_500 = "InternalServerError";

    public static final String PROPERTY_NOT_FOUND = "Property not found for id: %s";

}
