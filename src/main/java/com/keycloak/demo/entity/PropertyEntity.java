package com.keycloak.demo.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;

@Entity
public class PropertyEntity {

    @Id
    @GeneratedValue
    private Long id;

    private String name;

    private String address;
}
