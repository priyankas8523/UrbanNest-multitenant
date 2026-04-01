package com.keycloak.demo.module.property;

import com.keycloak.demo.entity.PropertyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PropertyRepository extends JpaRepository<PropertyEntity, Long> {

    Optional<PropertyEntity> findByUuid(UUID propertyId);
}
