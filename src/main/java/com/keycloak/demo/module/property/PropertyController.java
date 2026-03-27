package com.keycloak.demo.module.property;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class PropertyController {
    private final PropertyService propertyService;

}
