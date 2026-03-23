package com.keycloak.demo.enums;

public enum Status {
    ACTIVE("Active"),
    INACTIVE("Inactive");

    private final String name;

    Status(String name){ this.name = name;}

    public String getName() {return name;}
}
