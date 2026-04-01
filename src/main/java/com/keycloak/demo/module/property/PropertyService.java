package com.keycloak.demo.module.property;

import com.keycloak.demo.dto.Property;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.UUID;

public interface PropertyService {

    void createProperty(Property property);

    void updateProperty(Property property);

    ResponseEntity<Page<Property>> getAllProperties(int page, int pageSize, String search);

    ResponseEntity<Property> getPropertyById(UUID propertyId);



}
