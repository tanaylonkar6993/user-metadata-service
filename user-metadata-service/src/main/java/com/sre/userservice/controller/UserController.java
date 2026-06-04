package com.sre.userservice.controller;

import com.sre.userservice.dto.CreateUserRequest;
import com.sre.userservice.dto.UserResponse;
import com.sre.userservice.service.UserService;
import com.sre.userservice.service.UserService.CreateResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController         // Tells Spring to handle the incoming HTTP requests and all methods return JSON by default
@RequestMapping("/user")        //Maps a base url path to a controller class
@RequiredArgsConstructor        
@Tag(name = "User Metadata", description = "CRUD operations for user metadata")
public class UserController {

    static final String IDEMPOTENCY_KEY_HEADER = "X-Idempotency-Key";

    private final UserService userService;

    // -- POST /user ------------------------------------------------------------
    // Part where the User inputs details for creation of new user

    @Operation(
        summary = "Create a new user",
        description = """
            Idempotent endpoint - supply the X-Idempotency-Key header to prevent duplicate
            creation on retry. If the same key is received again, the existing user is returned
            with HTTP 200. A genuinely new user returns HTTP 201.
            """
    )
    @PostMapping
    public ResponseEntity<UserResponse> createUser(
            @Valid @RequestBody CreateUserRequest request,
            @Parameter(description = "Client-generated idempotency key (UUID recommended)")
            @RequestHeader(value = IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey) {

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            idempotencyKey = UUID.randomUUID().toString();
            log.warn("No {} header provided - auto-generated key={} (request is NOT idempotent)",
                     IDEMPOTENCY_KEY_HEADER, idempotencyKey);
        }

        CreateResult result = userService.createUser(request, idempotencyKey);

        HttpHeaders headers = new HttpHeaders();
        headers.add(IDEMPOTENCY_KEY_HEADER, idempotencyKey);

        return ResponseEntity
                .status(result.isReplay() ? HttpStatus.OK : HttpStatus.CREATED)
                .headers(headers)
                .body(result.getUser());
    }

    // -- GET /user/{id} --------------------------------------------------------

    @Operation(summary = "Get user by ID")
    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getUser(
            @Parameter(description = "Unique user ID") @PathVariable String id) {
        return ResponseEntity.ok(userService.getUser(id));
    }
}
