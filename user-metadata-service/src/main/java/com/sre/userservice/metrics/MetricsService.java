package com.sre.userservice.metrics;

import io.micrometer.core.instrument.*;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Central metrics registry for the User Metadata Service.
 *
 * Exposes the following Micrometer metrics (scraped by Prometheus at /actuator/prometheus):
 *
 *   user_service_requests_total        - total API calls
 *   user_service_success_total         - successful (2xx) responses
 *   user_service_failure_total         - failed (4xx/5xx) responses
 *   user_service_request_latency_ms    - histogram with P50/P95/P99 percentiles
 *   user_service_db_retry_total        - number of Resilience4j retry events
 *   user_service_circuit_breaker_open  - gauge: 1 when CB is OPEN, 0 otherwise
 */
@Service
@RequiredArgsConstructor
public class MetricsService {

    private final MeterRegistry meterRegistry;

    private Counter totalRequests;
    private Counter successCount;
    private Counter failureCount;
    private Counter dbRetryCount;
    private Timer   requestLatencyTimer;

    private final AtomicLong circuitBreakerOpen = new AtomicLong(0);

    @PostConstruct
    public void init() {
        totalRequests = Counter.builder("user_service_requests_total")
                .description("Total number of API requests received")
                .register(meterRegistry);

        successCount = Counter.builder("user_service_success_total")
                .description("Total successful (2xx) API responses")
                .register(meterRegistry);

        failureCount = Counter.builder("user_service_failure_total")
                .description("Total failed (4xx/5xx) API responses")
                .register(meterRegistry);

        dbRetryCount = Counter.builder("user_service_db_retry_total")
                .description("DB write retries triggered by the Resilience4j retry policy")
                .register(meterRegistry);

        requestLatencyTimer = Timer.builder("user_service_request_latency_ms")
                .description("End-to-end request latency in milliseconds")
                .publishPercentiles(0.5, 0.95, 0.99)
                .publishPercentileHistogram()
                .register(meterRegistry);

        Gauge.builder("user_service_circuit_breaker_open", circuitBreakerOpen, AtomicLong::doubleValue)
                .description("1 when the DB circuit breaker is OPEN; 0 when CLOSED or HALF_OPEN")
                .register(meterRegistry);
    }

    public void incrementTotalRequests()           { totalRequests.increment(); }
    public void incrementSuccess()                 { successCount.increment(); }
    public void incrementFailure()                 { failureCount.increment(); }
    public void incrementDbRetry()                 { dbRetryCount.increment(); }
    public void recordLatency(Duration duration)   { requestLatencyTimer.record(duration); }

    public void recordCircuitBreakerState(boolean isOpen) {
        circuitBreakerOpen.set(isOpen ? 1L : 0L);
    }
}
