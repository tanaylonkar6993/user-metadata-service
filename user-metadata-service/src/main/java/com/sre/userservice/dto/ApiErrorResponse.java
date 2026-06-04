package com.sre.userservice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import java.time.Instant;

@Data @Builder
public class ApiErrorResponse {
    private int    status;
    private String error;
    private String message;
    @JsonProperty("request_id")
    private String requestId;
    private Instant timestamp;
}
