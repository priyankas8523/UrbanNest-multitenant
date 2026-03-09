package com.keycloak.demo.controller;

import com.keycloak.demo.entity.PropertyEntity;
import com.keycloak.demo.repository.PropertyRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/properties")
public class PropertyController {

    private final PropertyRepository repository;

    public PropertyController(PropertyRepository repository) {
        this.repository = repository;
    }

    @PostMapping
    public PropertyEntity create(@RequestBody PropertyEntity property) {
        return repository.save(property);
    }

    @GetMapping
    public List<PropertyEntity> getAll() {
        return repository.findAll();
    }
}
