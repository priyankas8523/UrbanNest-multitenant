package com.keycloak.demo.module.property;

import com.keycloak.demo.dto.Literals;
import com.keycloak.demo.dto.Property;
import com.keycloak.demo.entity.PropertyEntity;
import com.keycloak.demo.module.common.Exception.UrbanNestException;
import com.keycloak.demo.module.common.UrbarNestService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.UUID;

@RequiredArgsConstructor
@Service
public class PropertyServiceImpl implements PropertyService, UrbarNestService {

    private final PropertyRepository propertyRepository;

    @Override
    public void createProperty(Property property) {
        PropertyEntity propertyEntity = mapDtoToEntity(property);
        propertyEntity.setUuid(property.getUuid());
        propertyEntity.setName(property.getName());
        propertyRepository.save(propertyEntity);
    }

    @Override
    public void updateProperty(Property property) {
        PropertyEntity propertyEntity = mapDtoToEntity(property);
        propertyEntity.setUuid(property.getUuid());
        propertyEntity.setName(property.getName());
        propertyRepository.save(propertyEntity);    }

    @Override
    public ResponseEntity<Page<Property>> getAllProperties(int page, int pageSize, String search) {
        return null;
    }

    private PropertyEntity findPropertyById(UUID propertyId){
        return propertyRepository.findByUuid(propertyId)
                .orElseThrow(() -> new UrbanNestException(Literals.EX_404, String.format(Literals.PROPERTY_NOT_FOUND, propertyId)));
    }

    @Override
    public ResponseEntity<Property> getPropertyById(UUID propertyId) {
        return data(mapEntityToDto(findPropertyById(propertyId)));
    }


    private static Property mapEntityToDto(PropertyEntity entity){
        if(entity == null){
            return null;
        }

        return Property.builder()
                .city(entity.getCity())
                .state(entity.getState())
                .address1(entity.getAddress1())
                .address2(entity.getAddress2())
                .postalCode(entity.getPostalCode())
                .build();

    }

    private static PropertyEntity mapDtoToEntity(Property dto){
        return PropertyEntity.builder()
                .city(dto.getCity())
                .state(dto.getState())
                .address1(dto.getAddress1())
                .address2(dto.getAddress2())
                .postalCode(dto.getPostalCode())
                .build();
    }
}
