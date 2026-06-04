package com.sre.userservice.exception;

import com.sre.userservice.dto.ApiErrorResponse;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.stream.Collectors;

/**
 * Maps exceptions to consistent HTTP error responses.
 * All error bodies include the MDC requestId for log correlation.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // 404
    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(UserNotFoundException ex) {
        log.warn("User not found | requestId={} | {}", MDC.get("requestId"), ex.getMessage());
        return build(HttpStatus.NOT_FOUND, "Not Found", ex.getMessage());
    }

    // 409
    @ExceptionHandler(DuplicateUserException.class)
    public ResponseEntity<ApiErrorResponse> handleDuplicate(DuplicateUserException ex) {
        log.warn("Duplicate user | requestId={} | {}", MDC.get("requestId"), ex.getMessage());
        return build(HttpStatus.CONFLICT, "Conflict", ex.getMessage());
    }

    // 400 - bean validation
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String details = ex.getBindingResult().getFieldErrors()
                .stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.warn("Validation failed | requestId={} | {}", MDC.get("requestId"), details);
        return build(HttpStatus.BAD_REQUEST, "Validation Failed", details);
    }

    // 503 - circuit breaker open
    @ExceptionHandler(CallNotPermittedException.class)
    public ResponseEntity<ApiErrorResponse> handleCircuitOpen(CallNotPermittedException ex) {
        log.error("Circuit breaker OPEN | requestId={}", MDC.get("requestId"));
        return build(HttpStatus.SERVICE_UNAVAILABLE, "Service Unavailable",
                "Database circuit breaker is OPEN - please retry after a short delay");
    }

    // 500 - catch-all
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGeneral(Exception ex) {
        log.error("Unhandled error | requestId={} | {}", MDC.get("requestId"), ex.getMessage(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error",
                "An unexpected error occurred");
    }

    private ResponseEntity<ApiErrorResponse> build(HttpStatus status, String error, String message) {
        return ResponseEntity.status(status).body(
                ApiErrorResponse.builder()
                        .status(status.value())
                        .error(error)
                        .message(message)
                        .requestId(MDC.get("requestId"))
                        .timestamp(Instant.now())
                        .build());
    }
}
