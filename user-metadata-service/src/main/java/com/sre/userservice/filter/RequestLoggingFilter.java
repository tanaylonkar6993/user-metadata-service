package com.sre.userservice.filter;

import com.sre.userservice.metrics.MetricsService;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Servlet filter that runs on every request and:
 *
 *  1. Reads or generates a request ID (X-Request-ID header), binds it to MDC
 *     so every subsequent log line in this thread includes it automatically.
 *  2. Echoes the request ID back to the caller in the response header.
 *  3. Logs request start and end (method, URI, status, latency).
 *  4. Increments Micrometer counters and records latency via MetricsService.
 */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class RequestLoggingFilter implements Filter {

    static final String REQUEST_ID_HEADER = "X-Request-ID";

    private final MetricsService metricsService;

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest  httpReq = (HttpServletRequest)  req;
        HttpServletResponse httpRes = (HttpServletResponse) res;

        // Assign request ID
        String requestId = httpReq.getHeader(REQUEST_ID_HEADER);
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }
        MDC.put("requestId", requestId);
        httpRes.setHeader(REQUEST_ID_HEADER, requestId);

        Instant start = Instant.now();
        metricsService.incrementTotalRequests();

        log.info("START  {} {} requestId={}", httpReq.getMethod(), httpReq.getRequestURI(), requestId);

        try {
            chain.doFilter(req, res);
        } finally {
            Duration latency = Duration.between(start, Instant.now());
            int      status  = httpRes.getStatus();

            metricsService.recordLatency(latency);

            if (status >= 200 && status < 400) {
                metricsService.incrementSuccess();
            } else {
                metricsService.incrementFailure();
            }

            log.info("END    {} {} status={} latencyMs={} requestId={}",
                     httpReq.getMethod(), httpReq.getRequestURI(),
                     status, latency.toMillis(), requestId);

            MDC.clear();
        }
    }
}
