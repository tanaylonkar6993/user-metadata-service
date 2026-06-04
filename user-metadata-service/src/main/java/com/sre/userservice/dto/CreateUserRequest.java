package com.sre.userservice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class CreateUserRequest {

    @NotBlank(message = "user_id is required")
    @JsonProperty("user_id")
    private String userId;

    @NotBlank(message = "name is required")
    private String name;

    @NotBlank(message = "email is required")
    @Email(message = "email must be a valid address")
    private String email;

    @NotBlank(message = "phone is required")
    @Pattern(regexp = "^\\+?[0-9]{7,15}$", message = "phone must be 7-15 digits, optionally prefixed with +")
    private String phone;
}
