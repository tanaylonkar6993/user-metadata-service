package com.sre.userservice.config;

import com.sre.userservice.metrics.MetricsService;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

/**
 * Wires Resilience4j event listeners to structured logging and custom metrics.
 *
 * Circuit Breaker events -> log WARN on state transitions, log DEBUG on success,
 *                          drive the user_service_circuit_breaker_open gauge.
 * Retry events           -> log WARN on each retry attempt,
 *                          increment user_service_db_retry_total counter.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class ResilienceConfig {

    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final RetryRegistry          retryRegistry;
    private final MetricsService         metricsService;

    @PostConstruct
    public void registerEventListeners() {

        // -- Circuit Breaker: userDb ----------------------------------------
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("userDb");

        cb.getEventPublisher()
          .onStateTransition(event -> {
              CircuitBreaker.State to = event.getStateTransition().getToState();
              log.warn("CircuitBreaker[userDb] state -> {}", to);
              metricsService.recordCircuitBreakerState(to == CircuitBreaker.State.OPEN);
          })
          .onError(e ->
              log.error("CircuitBreaker[userDb] recorded error: {}", e.getThrowable().getMessage()))
          .onSuccess(e ->
              log.debug("CircuitBreaker[userDb] success ({}ms)", e.getElapsedDuration().toMillis()));

        // -- Retry: userDb -------------------------------------------------
        Retry retry = retryRegistry.retry("userDb");

        retry.getEventPublisher()
             .onRetry(e -> {
                 log.warn("Retry[userDb] attempt #{} - cause: {}",
                          e.getNumberOfRetryAttempts(), e.getLastThrowable().getMessage());
                 metricsService.incrementDbRetry();
             })
             .onError(e ->
                 log.error("Retry[userDb] exhausted after {} attempts - cause: {}",
                           e.getNumberOfRetryAttempts(), e.getLastThrowable().getMessage()))
             .onSuccess(e ->
                 log.debug("Retry[userDb] succeeded after {} attempt(s)",
                           e.getNumberOfRetryAttempts()));
    }
}
