package com.keycloak.demo.module.property;

import com.keycloak.demo.dto.Property;
import com.keycloak.demo.module.common.Exception.UrbanNestException;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.UUID;

public interface PropertyService {

    void createProperty(Property property) throws UrbanNestException;

    void updateProperty(Property property) throws UrbanNestException;

    ResponseEntity<Page<Property>> getAllProperties(int page, int pageSize, String search) throws UrbanNestException;

    ResponseEntity<Property> getPropertyById(UUID propertyId) throws UrbanNestException;



}
