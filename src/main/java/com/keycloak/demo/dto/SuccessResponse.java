package com.keycloak.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
@AllArgsConstructor
public class SuccessResponse {
    private int status;
    private ResponseCode code;
    private String message;
    private String path;

    @Builder.Default
    private Instant timestamp = Instant.now();
}
