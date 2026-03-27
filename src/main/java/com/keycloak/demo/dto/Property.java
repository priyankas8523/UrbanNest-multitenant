package com.keycloak.demo.dto;

import com.keycloak.demo.enums.Status;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

@SuperBuilder
@Data
@NoArgsConstructor
public class Property {

    private UUID uuid;

    @NotNull(message = "Property Name cannot be null or empty")
    private String name;

    @NotNull(message = "Address cannot be null or empty")
    private String address1;

    private String address2;

    @NotNull(message = "City cannot be null or empty")
    private String city;

    @NotNull(message = "State cannot be null or empty")
    private String state;

    @NotNull(message = "Postal Code cannot be null or empty")
    private String postalCode;

    private Status status;

}

