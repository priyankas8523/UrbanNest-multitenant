package com.keycloak.demo.module.common;

import com.keycloak.demo.dto.SuccessResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

public interface UrbarNestService {

    default ResponseEntity<SuccessResponse> success(String message) {
        return success(HttpStatus.OK, message);
    }

    default ResponseEntity<SuccessResponse> success(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(SuccessResponse.builder().status(status.value()).message(message).build());
    }

    default <T> ResponseEntity<T> data(T data) {
        return ResponseEntity.ok(data);
    }

    default <T> ResponseEntity<T> data(int statusCode, T data) {
        return ResponseEntity.status(statusCode).body(data);
    }


}
