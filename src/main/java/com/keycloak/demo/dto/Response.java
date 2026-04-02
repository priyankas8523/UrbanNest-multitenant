package com.keycloak.demo.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Value;
import tools.jackson.databind.annotation.JsonDeserialize;

import java.util.Date;
import java.util.Map;

@Value
@Builder
public class Response {

    @Builder.Default
    private Date date = new Date();
    private ResponseCode code;

    @JsonDeserialize(as = String.class)
    private Object message;

    @JsonDeserialize(as = String.class)
    private Object data;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Map<String, String> errors;
    private String path;
    private String requestId;
    private String version;

}
