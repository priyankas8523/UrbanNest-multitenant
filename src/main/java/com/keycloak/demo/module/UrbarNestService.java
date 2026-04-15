package com.keycloak.demo.module.common;

import com.keycloak.demo.dto.ResponseCode;
import com.keycloak.demo.dto.SuccessResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

public interface UrbarNestService {

    default ResponseEntity<SuccessResponse> success(String message) {
        return success(HttpStatus.OK, message);
    }

    default ResponseEntity<SuccessResponse> success(ResponseCode responseCode) {
        return success(responseCode);
    }

    default ResponseEntity<SuccessResponse> success(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(SuccessResponse.builder().status(status.value()).message(message).build());
    }

//    public ResponseEntity<SuccessResponse> success(ResponseCode code, String... fields) {
//        return new ResponseEntity<>(SuccessResponse.builder()
//                .code(code)
//                .message(messageService.getMessage(code, fields))
//                .path(httpServletRequest.getRequestURI())
//                .requestId(UUID.randomUUID().toString())
//                .version(AppConfig.getAppVersion())
//                .build(), code.name().contains("CREATED")? HttpStatus.CREATED : HttpStatus.OK);
//    }

    default <T> ResponseEntity<T> data(T data) {
        return ResponseEntity.ok(data);
    }

    default <T> ResponseEntity<T> data(int statusCode, T data) {
        return ResponseEntity.status(statusCode).body(data);
    }


}
