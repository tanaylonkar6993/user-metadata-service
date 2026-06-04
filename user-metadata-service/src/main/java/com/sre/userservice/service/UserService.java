package com.sre.userservice.service;

import com.sre.userservice.dto.CreateUserRequest;
import com.sre.userservice.dto.UserResponse;
import com.sre.userservice.exception.DuplicateUserException;
import com.sre.userservice.exception.UserNotFoundException;
import com.sre.userservice.model.User;
import com.sre.userservice.repository.UserRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Slf4j      // Injects a static log field using (log.info(), log.error(), etc.)
@Service
@RequiredArgsConstructor        //Constructor for all final fields--used with dependency injection
public class UserService {

    private final UserRepository userRepository;

    // -- Inner result type -----------------------------------------------------

    @Data
    public static class CreateResult {
        private final UserResponse user;
        /** True when the request was an idempotent replay (no new row created). */
        private final boolean replay;
    }

    // -- CREATE USER -----------------------------------------------------------

    /**
     * Idempotency contract
     * ---------------------
     * * Callers SHOULD supply X-Idempotency-Key on every POST /user request.
     * * Same key + any body  -> return the originally created user (HTTP 200).
     * * Different key, same e-mail -> HTTP 409 (business duplicate).
     * * New key, new e-mail  -> persist and return (HTTP 201).
     *
     * The DB write is wrapped by @Retry (exponential back-off + jitter) and
     * @CircuitBreaker; both are configured in application.yml under the
     * "userDb" instance name.
     */
    @Transactional
    //@Transactional means the entire method runs in one DB transaction — if anything fails mid-way, the whole thing rolls back.
    public CreateResult createUser(CreateUserRequest request, String idempotencyKey) {

        // 1. Idempotency check -------------------------------------------------
        //If the same idempotencyKey already exists in the DB (meaning the client already sent this request),
        //  it returns the original user without creating a new one. 
        // This makes retries safe — a network timeout won't create duplicate users.-----------

        Optional<User> byKey = userRepository.findByIdempotencyKey(idempotencyKey);
        if (byKey.isPresent()) {
            log.info("Idempotent replay | requestId={} | key={} | userId={}",
                     MDC.get("requestId"), idempotencyKey, byKey.get().getUserId());
            return new CreateResult(toResponse(byKey.get()), true);
        }

        // 2. Duplicate e-mail check --------------------------------------------
        //If a different request tries to register an already-used email, 
        // it throws a DuplicateUserException -> mapped to HTTP 409 Conflict --------
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new DuplicateUserException(
                    "A user with email '" + request.getEmail() + "' already exists");
        }

        // 3. Persist (retry + circuit-breaker guarded) -------------------------
        User saved = persistUser(request, idempotencyKey);
        log.info("User created | requestId={} | userId={}", MDC.get("requestId"), saved.getUserId());
        return new CreateResult(toResponse(saved), false);
    }

    /**
     * The actual DB write, decorated with Resilience4j annotations.
     *
     * Retry (application.yml -> resilience4j.retry.instances.userDb):
     *   maxAttempts                = 3
     *   waitDuration               = 500ms  (base)
     *   enableExponentialBackoff   = true
     *   exponentialBackoffMultiplier = 2
     *   randomizedWaitFactor       = 0.3    ← adds ±30 % jitter
     *
     * CircuitBreaker (application.yml -> resilience4j.circuitbreaker.instances.userDb):
     *   slidingWindowSize          = 10
     *   failureRateThreshold       = 50
     *   waitDurationInOpenState    = 10s
     */
    @Retry(name = "userDb", fallbackMethod = "persistUserFallback")
    @CircuitBreaker(name = "userDb", fallbackMethod = "persistUserFallback")
    public User persistUser(CreateUserRequest request, String idempotencyKey) {
        User user = User.builder()
                .userId(request.getUserId())
                .name(request.getName())
                .email(request.getEmail())
                .phone(request.getPhone())
                .createdAt(Instant.now())
                .idempotencyKey(idempotencyKey)
                .build();
        return userRepository.save(user);
    }

    /** Fallback - all retry attempts exhausted or circuit is open. */
    @SuppressWarnings("unused")
    public User persistUserFallback(CreateUserRequest request, String idempotencyKey, Throwable t) {
        log.error("persistUser fallback | requestId={} | cause={}",
                  MDC.get("requestId"), t.getMessage());
        throw new RuntimeException("Unable to persist user after retries: " + t.getMessage(), t);
    }



    // -- GET USER --------------------------------------------------------------

    @CircuitBreaker(name = "userDb", fallbackMethod = "getUserFallback")
    public UserResponse getUser(String userId) {
        return userRepository.findById(userId)
                .map(this::toResponse)
                .orElseThrow(() -> new UserNotFoundException(userId));
    }

    @SuppressWarnings("unused")
    public UserResponse getUserFallback(String userId, Throwable t) {
        log.error("getUser fallback | requestId={} | userId={} | cause={}",
                  MDC.get("requestId"), userId, t.getMessage());
        throw new RuntimeException("Unable to fetch user - database unavailable: " + t.getMessage(), t);
    }

    // -- Helper ----------------------------------------------------------------

    private UserResponse toResponse(User u) {
        return UserResponse.builder()
                .userId(u.getUserId())
                .name(u.getName())
                .email(u.getEmail())
                .phone(u.getPhone())
                .createdAt(u.getCreatedAt())
                .build();
    }
}
