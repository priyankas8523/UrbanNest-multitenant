package com.keycloak.demo.module.property;

import com.keycloak.demo.dto.Property;
import com.keycloak.demo.dto.ResponseCode;
import com.keycloak.demo.dto.SuccessResponse;
import com.keycloak.demo.module.common.Exception.UrbanNestException;
import com.keycloak.demo.module.UrbarNestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/property")
@RequiredArgsConstructor
public class PropertyController implements UrbarNestService {
    private final PropertyService propertyService;

    /**
     * Property manager can create a Property
     * Requires WRITE permission 
     **/
    @PostMapping
    public ResponseEntity<SuccessResponse> createProperty(@Valid @RequestBody Property property) throws UrbanNestException {
        propertyService.createProperty(property);
        return success(ResponseCode.PROPERTY_CREATED);
    }

    /**
     * Property manager can create a Property
     * Requires WRITE permission 
     **/
    @PutMapping
    public ResponseEntity<SuccessResponse> updateProperty(@Valid @RequestBody Property property) throws UrbanNestException {
        propertyService.updateProperty(property);
        return success(ResponseCode.PROPERTY_UPDATED);
    }

    /**
     * Get All Properties
     * Requires READ permission 
     **/
    @GetMapping("/all")
    public ResponseEntity<Page<Property>> getAllProperties(@RequestParam int page, @RequestParam int pageSize, @RequestParam(required = false) String search) throws UrbanNestException {
        return propertyService.getAllProperties(page, pageSize, search);
    }

    /**
     * Get Property Details
     * Requires READ permission 
     **/
    @GetMapping("/{propertyId}")
    public ResponseEntity<Property> getProperty(@PathVariable UUID propertyId) throws UrbanNestException {
        return propertyService.getPropertyById(propertyId);
    }
    
    
    
    

}
