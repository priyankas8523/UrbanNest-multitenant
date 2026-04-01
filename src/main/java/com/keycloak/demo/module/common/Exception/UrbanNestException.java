package com.keycloak.demo.module.common.Exception;


import org.springframework.http.HttpStatus;

import static com.keycloak.demo.dto.Literals.*;

public class UrbanNestException extends RuntimeException {

    final String exceptionType;

    public UrbanNestException(String message, String exceptionType) {
        super(message);
        this.exceptionType = exceptionType;
    }

    public UrbanNestException(String exceptionType, String message, Throwable cause) {
        super(message, cause);
        this.exceptionType = exceptionType;
    }

    public int getHttpStatusCode() {
        return switch (exceptionType) {
            case EX_400 -> HttpStatus.BAD_REQUEST.value();
            case EX_404 -> HttpStatus.NOT_FOUND.value();
            case EX_409 -> HttpStatus.CONFLICT.value();
            default -> HttpStatus.INTERNAL_SERVER_ERROR.value();
        };
    }



}
