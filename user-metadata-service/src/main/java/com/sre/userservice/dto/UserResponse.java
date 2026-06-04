package com.sre.userservice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import java.time.Instant;

@Data @Builder
public class UserResponse {
    @JsonProperty("user_id")
    private String userId;
    private String name;
    private String email;
    private String phone;
    @JsonProperty("created_at")
    private Instant createdAt;
}
