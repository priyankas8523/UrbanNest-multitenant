package com.keycloak.demo.entity;

import com.keycloak.demo.enums.Status;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import jakarta.validation.constraints.Size;

import java.util.UUID;

@Entity
@Table(name = "PROPERTY")
@Data
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
public class PropertyEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    private UUID uuid;

    @NotBlank
    @Size(max = 100)
    @Column(name = "PROPERTY_NAME")
    private String name;

    @NotBlank
    @Size(max = 100)
    @Column(name = "ADDR1")
    private String address1;

    @NotBlank
    @Size(max = 100)
    @Column(name = "ADDR2")
    private String address2;

    @NotBlank
    @Size(max = 100)
    @Column(name = "CITY")
    private String city;

    @NotBlank
    @Size(max = 50)
    @Column(name = "STATE")
    private String state;

    @NotBlank
    @Size(max = 10)
    @Column(name = "POSTAL_CODE")
    private String postalCode;

    @Column(name = "STATUS")
    @Enumerated(EnumType.STRING)
    private Status status;

}
