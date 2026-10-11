# Task 9.0: Observability and Operational Readiness

<critical>Read the prd.md and techspec.md files in this folder. If you do not read these files, your task will be invalidated.</critical>

## Overview

Make the running service observable and well-behaved in operation: metrics, structured logs, health probes, graceful shutdown, timeouts and API documentation.

**Execution:** stage 7, in parallel with task 8.0. Starts when 6.0 is merged. Follow the file ownership and parallel-agent rules in `tasks.md`: do not edit `LedgerService` or anything under `outbox/`. Controllers may be changed only to add documentation annotations.

<skills>
### Standard Skills Compliance

None. The project has no `.claude/skills` directory.
</skills>

<requirements>
- Metrics in Prometheus format at `/actuator/prometheus`: `wallet_operations_total{type,outcome}` and `wallet_operation_duration_seconds{type}`. The outbox gauges belong to task 8.0.
- Operation metrics are recorded from outside `LedgerService`, by an aspect or interceptor in `observability/`, so this task does not edit that class.
- Logs are JSON, one line per event, carrying `requestId`, `walletId` and `operationId` where known.
- A `requestId` is taken from an incoming header or generated, and returned in the response.
- Log levels: business rejections at INFO, lock timeouts at WARN, unexpected failures at ERROR.
- Liveness and readiness probes are exposed; readiness includes the database.
- Graceful shutdown is enabled.
- The HikariCP pool size and connection timeout are set explicitly.
- springdoc serves the OpenAPI document and Swagger UI, with every endpoint and error code described.
- Metrics and logging stay out of the business methods' bodies, so `LedgerService` still reads as the write flow.
</requirements>

## Subtasks

- [ ] 9.1 Record operation counters and timers by type and outcome
- [ ] 9.2 Configure JSON logging and the request-id filter
- [ ] 9.3 Enable probes, graceful shutdown and explicit pool settings
- [ ] 9.4 Annotate the API for springdoc
- [ ] 9.5 Write the tests

## Implementation Details

- Metrics and logs: `techspec.md` section 8, "Monitoring and Observability".
- Availability settings: section 8, "Availability".
- Keeping business code readable: section 9.1.

## Success Criteria

- After one deposit and one rejected withdrawal, the Prometheus endpoint shows both counted under the right `type` and `outcome`.
- The readiness probe reports UP with the database available.
- The OpenAPI document lists all seven endpoints.
- The whole suite and the JaCoCo gate pass.

## Task Tests

- [ ] Unit tests: none
- [ ] Integration tests:
  - A successful deposit increments `wallet_operations_total{type="DEPOSIT",outcome="SUCCESS"}`
  - A rejected withdrawal increments the counter with the rejection outcome
  - A response carries the request id, and an incoming request id is echoed
  - Liveness and readiness probes return UP
  - The OpenAPI document is served and contains every endpoint
- [ ] E2E tests: not applicable

<critical>ALWAYS CREATE AND RUN THE TASK'S TESTS BEFORE CONSIDERING IT FINISHED</critical>

## Relevant Files

- `src/main/java/com/recargapay/wallet/observability/OperationMetrics.java`
- `src/main/java/com/recargapay/wallet/observability/RequestIdFilter.java`
- `src/main/java/com/recargapay/wallet/config/OpenApiConfig.java`
- `src/main/resources/application.yml`
- `src/main/resources/logback-spring.xml`
- `src/test/java/com/recargapay/wallet/observability/OperationMetricsTest.java`
- `src/test/java/com/recargapay/wallet/observability/RequestIdTest.java`
- `src/test/java/com/recargapay/wallet/observability/OperationalEndpointsTest.java`
