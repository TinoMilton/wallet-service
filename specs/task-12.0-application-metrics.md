# Task 12.0: Application Metrics for Monitoring

<critical>Read AGENTS.md and techspec.md sections 8, 9.1 and 9.3 before starting.</critical>

## Overview

The dashboards of task 13.0 need three things the service does not publish yet: latency histograms (without them no p95 or p99 can be computed), the reason code of every rejected request, and a count of idempotent replays.

## Requirements

- `http_server_requests_seconds` and `wallet_operation_duration_seconds` are served with histogram buckets (`_bucket` series) at `/actuator/prometheus`. Configuration only, in `application.yml`.
- Every served meter carries the common tag `application="wallet-service"`.
- New counter `wallet_api_problems_total{code}`: incremented once for every problem detail the API answers with, `code` being the problem's `code`. It is counted where the problem is produced in `GlobalExceptionHandler`, so the codes are not listed a second time anywhere.
- New counter `wallet_idempotent_replays_total{type}`: incremented once for every successful idempotent replay, `type` being DEPOSIT, WITHDRAWAL or TRANSFER. It is counted in the `observability` package by an aspect around `LedgerService.replay`; `LedgerService` and `IdempotentCommandExecutor` stay free of metrics code. A first execution does not count; a replay rejected with `IDEMPOTENCY_KEY_REUSED` does not count.
- The served names must be exactly the ones above. Task 13.0 builds its dashboards on them in parallel.
- No money amount is ever written to a meter.
- No new dependency, no migration, no change to any HTTP contract.

## Subtasks

- [ ] 12.1 Enable the histograms and the common tag in `application.yml`
- [ ] 12.2 Count problems by code
- [ ] 12.3 Count idempotent replays by type
- [ ] 12.4 Tests
- [ ] 12.5 Update techspec.md section 8 ("Monitoring and Observability") with the new meters and the histograms. Leave the "Dashboards" bullet alone; task 14.0 owns it.

## Task Tests

One behaviour per test, given/when/then, in the style of the existing observability tests. These are not business rules, so no BR id.

- [ ] Buckets are served for the HTTP request timer
- [ ] Buckets are served for the operation timer
- [ ] The common tag is served
- [ ] A rejected request increments the counter of its own code (for example `INSUFFICIENT_FUNDS`)
- [ ] A replay increments the replay counter of its type
- [ ] A first execution does not increment the replay counter
- [ ] A key reused with a different request does not increment the replay counter

Tests share one Spring context and one registry: assert on the difference before and after, never on absolute values.

## Files

- `src/main/resources/application.yml`
- `src/main/java/com/recargapay/wallet/observability/` (new or existing classes)
- `src/main/java/com/recargapay/wallet/api/GlobalExceptionHandler.java`
- `src/test/java/com/recargapay/wallet/observability/`
- `specs/techspec.md` (section 8 only)

Must not edit: `docker-compose.yml`, `Dockerfile`, `README.md`, anything under `monitoring/`, `pom.xml`, migrations, `specs/tasks.md`.

## Success Criteria

- `./mvnw spotless:apply` then `./mvnw verify` exits 0 with the JaCoCo gate passing.
- Every existing test passes unchanged.
