# User Metadata Service

A production-grade Spring Boot microservice demonstrating core SRE reliability patterns — idempotency, retry with exponential backoff and jitter, circuit breaker, custom metrics, and structured logging.

---

## Why This Project

Building a reliable API is not just about making it work — it's about making it work correctly under failure conditions. This service is designed to handle real-world problems: duplicate requests from retrying clients, transient database failures, and cascading failures when the database is overwhelmed.

Every reliability pattern here is production-motivated, not academic.

---

## Features

**Idempotency**
Clients supply an `X-Idempotency-Key` header on every `POST /user` request. The key is stored in the database with a unique constraint. If the same key arrives again, the service returns the original response without writing to the database — safe retries with zero duplicates.

**Retry with Exponential Backoff + Jitter**
Transient database failures trigger automatic retries via Resilience4j. Three attempts with a 500ms base wait, 2x multiplier, and ±30% random jitter to prevent thundering herd on recovery.

**Circuit Breaker**
A Resilience4j circuit breaker monitors the database layer. Opens after 50% failure rate over 10 calls, stays open for 10 seconds, then probes with 3 calls before closing. Returns HTTP 503 immediately when open — no wasted threads.

**Custom Metrics (Micrometer + Prometheus)**
Six custom metrics exposed at `/actuator/prometheus`:
- `user_service_requests_total` — total API requests
- `user_service_success_total` — successful responses
- `user_service_failure_total` — failed responses
- `user_service_request_latency_ms` — histogram with P50/P95/P99
- `user_service_db_retry_total` — retry events
- `user_service_circuit_breaker_open` — circuit breaker state gauge

**Structured Logging with MDC**
Every log line includes the `requestId` via MDC (Mapped Diagnostic Context). A servlet filter injects the ID at request start and clears it at completion — full request traceability with zero boilerplate in business logic.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 3.2 |
| Resilience | Resilience4j 2.2 |
| Metrics | Micrometer + Prometheus |
| ORM | Spring Data JPA + Hibernate |
| Database (prod) | PostgreSQL 16 |
| Database (dev) | H2 in-memory |
| Containerisation | Docker (multi-stage build) |
| Orchestration | Docker Compose / Kubernetes |
| CI/CD | Jenkins |
| Observability | Prometheus + Grafana |
| API Docs | SpringDoc OpenAPI 3.0 |

---

## API Endpoints

### POST /user — Create a user

```http
POST /user
Content-Type: application/json
X-Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000

{
  "user_id": "usr-001",
  "name":    "Alice Smith",
  "email":   "alice@example.com",
  "phone":   "+14155552671"
}
```

| Response | Condition |
|---|---|
| 201 Created | New user created successfully |
| 200 OK | Idempotent replay — existing user returned |
| 400 Bad Request | Validation failure |
| 409 Conflict | Email already registered |
| 503 Service Unavailable | Circuit breaker is open |

### GET /user/{id} — Fetch a user

```http
GET /user/usr-001
```

| Response | Condition |
|---|---|
| 200 OK | User found |
| 404 Not Found | No user with that ID |
| 503 Service Unavailable | Circuit breaker is open |

---

## Running Locally

### Prerequisites
- Docker and Docker Compose
- Java 17 (for running without Docker)
- Maven 3.9+

### With Docker Compose (recommended)

```bash
git clone https://github.com/tanaylonkar17/user-metadata-service.git
cd user-metadata-service
docker-compose up --build
```

| URL | Description |
|---|---|
| http://localhost:8080/swagger-ui.html | Interactive API docs |
| http://localhost:8080/actuator/health | Health check |
| http://localhost:8080/actuator/prometheus | Raw metrics |
| http://localhost:9090 | Prometheus |
| http://localhost:3000 (admin/admin) | Grafana dashboard |

### Without Docker (H2 in-memory DB)

```bash
cd user-metadata-service
mvn spring-boot:run
```

### Run Tests

```bash
mvn test
```

---

## Resilience Configuration

Configured in `src/main/resources/application.yml`:

```yaml
resilience4j:
  retry:
    instances:
      userDb:
        max-attempts: 3
        wait-duration: 500ms
        enable-exponential-backoff: true
        exponential-backoff-multiplier: 2
        randomized-wait-factor: 0.3       # +-30% jitter

  circuitbreaker:
    instances:
      userDb:
        sliding-window-size: 10
        failure-rate-threshold: 50
        wait-duration-in-open-state: 10s
        permitted-number-of-calls-in-half-open-state: 3
```

---

## Project Structure

```
src/main/java/com/sre/userservice/
├── controller/
│   └── UserController.java           # REST endpoints
├── service/
│   ├── UserService.java              # Business logic + idempotency
│   └── UserPersistenceService.java   # @Retry + @CircuitBreaker (separate bean for AOP)
├── repository/
│   └── UserRepository.java           # JPA repository
├── model/
│   └── User.java                     # JPA entity
├── dto/
│   ├── CreateUserRequest.java        # Input DTO with validation
│   ├── UserResponse.java             # Output DTO
│   └── ApiErrorResponse.java         # Error response DTO
├── metrics/
│   └── MetricsService.java           # Custom Micrometer metrics
├── config/
│   └── ResilienceConfig.java         # CB + retry event listeners
├── exception/
│   └── GlobalExceptionHandler.java   # Centralised exception handling
└── filter/
    └── RequestLoggingFilter.java     # MDC request ID + latency logging
```

---

## Key Design Decisions

**Why a separate UserPersistenceService bean?**
Spring AOP creates a proxy around beans to intercept `@Retry` and `@CircuitBreaker` annotations. When a method calls another method on the same class (`this.persistUser()`), it bypasses the proxy and the annotations do nothing. Moving the DB write to a separate `@Service` bean ensures all calls go through the proxy correctly.

**Why store the idempotency key in the database?**
An in-memory cache would lose keys on restart, making restarts dangerous. Storing in the database with a unique constraint gives persistence and hard deduplication guarantees at the storage level — even if application code has a bug, the database rejects the duplicate.

**Why jitter on retry backoff?**
Without jitter, all retrying threads wait exactly the same duration and hit the database simultaneously on recovery — a thundering herd that can re-trigger the failure. Random jitter spreads retries across a window, smoothing the recovery load.

---

## CI/CD

Jenkins pipeline defined in `Jenkinsfile`:

```
Checkout -> Test -> Build JAR -> Docker Build -> Push to AWS ECR
```

Images are tagged with the git commit SHA and build number (e.g. `a1b2c3d-12`) for full traceability.
