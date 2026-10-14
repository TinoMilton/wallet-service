# Task 13.0: Monitoring Stack and Dashboards

<critical>Read AGENTS.md, techspec.md sections 5 (data model) and 8, `docker-compose.yml` and `src/main/resources/db/migration/V1__create_schema.sql` before starting.</critical>

## Overview

Ship Prometheus, a PostgreSQL exporter and Grafana as an optional part of the compose stack, with three dashboards provisioned from files. Rates, latency and saturation come from Prometheus. Money figures come from PostgreSQL through a read-only role, so they are exact `NUMERIC` values and never a double.

## Requirements

### Compose

- `docker compose up` keeps starting only the application and PostgreSQL, exactly as today.
- `docker compose --profile monitoring up` also starts:
  - `prometheus`: scrapes the application at `/actuator/prometheus` and the exporter every 5 seconds.
  - `postgres-exporter`: server-side database metrics.
  - `grafana-db-reader`: a one-shot container that runs `psql` once PostgreSQL is healthy and creates, idempotently, a login role that can only `SELECT` on the application tables. It is here, not in a Flyway migration, so monitoring does not leak into the application schema.
  - `grafana`: datasources and dashboards provisioned from files, anonymous read-only access (local use only; say so in a comment), the three dashboards in one folder, one of them as the home dashboard.
- Host ports come from environment variables with defaults, documented in the header comment like the existing ones: `WALLET_GRAFANA_PORT` (3000), `WALLET_PROMETHEUS_PORT` (9090). The exporter publishes no host port.
- Image versions are pinned to a specific current stable tag, never `latest`. Check that the exporter version supports PostgreSQL 18.
- No credential other than the existing local defaults and the read-only role's local password.

### Dashboards

Provisioned JSON, datasources referenced by fixed uid, default time range 15 minutes, auto refresh 10 seconds (1 minute for the business dashboard). Every panel has a title a person understands and a unit. Latency in seconds or milliseconds, shares in percent.

**"Wallet — Service"** (Prometheus)

- Requests per second, total and by endpoint (`uri`, `method`), excluding `/actuator`
- Response time by endpoint: average, p50, p95, p99 (`histogram_quantile` on `http_server_requests_seconds_bucket`)
- Share of 5xx answers and share of 4xx answers
- Money operations per second by `type` and `outcome` (`wallet_operations_total`)
- Operation p95 and p99 by type (`wallet_operation_duration_seconds_bucket`)
- Lock contention: rate of `outcome="BUSY"`
- Problems per second by reason code (`wallet_api_problems_total`)
- Idempotent replays per second by type (`wallet_idempotent_replays_total`)
- Outbox: unpublished events and age of the oldest (`wallet_outbox_unpublished`, `wallet_outbox_lag_seconds`)

**"Wallet — Runtime and Database"** (Prometheus)

- CPU: process, system, load average
- Memory: heap and non-heap used against max
- Garbage collection: pause time and pauses per second
- Live threads
- Connection pool: active, idle, pending, against the maximum; time to obtain a connection; pool timeouts
- PostgreSQL: connections by state against `max_connections`, commits and rollbacks per second, deadlocks, locks by mode, database size
- Targets up

**"Wallet — Business"** (PostgreSQL datasource)

- **Ledger integrity:** number of wallets whose `balance` differs from the sum of their ledger entries. A stat panel, green at 0, red above.
- **Transfer conservation:** number of transfer operations whose entries do not sum to zero. Same colouring.
- Total money held (sum of wallet balances)
- Wallets in total, and new wallets over time
- Volume moved over time by operation type
- Net flow: deposits minus withdrawals
- Average ticket by operation type
- Operations over time by type
- Busiest wallets by number of entries (top 10)

Money is shown with two decimals, as the database returns it.

### Names owned by task 12.0

Task 12.0 runs in parallel and adds: histogram buckets for the two timers, the common tag `application="wallet-service"`, `wallet_api_problems_total{code}` and `wallet_idempotent_replays_total{type}`. Build the panels on these exact names. In this task's worktree they do not exist yet, so those panels will be empty; say which ones in your summary. Do not edit Java code or `application.yml` to make them appear.

## Subtasks

- [x] 13.1 Compose services under the `monitoring` profile
- [x] 13.2 Prometheus configuration
- [x] 13.3 Read-only role script and one-shot service
- [x] 13.4 Grafana provisioning (datasources, dashboard provider)
- [x] 13.5 The three dashboards
- [x] 13.6 Verification run

## Files

- `docker-compose.yml`
- `monitoring/prometheus/prometheus.yml`
- `monitoring/postgres/create-grafana-reader.sql`
- `monitoring/grafana/provisioning/datasources/datasources.yml`
- `monitoring/grafana/provisioning/dashboards/dashboards.yml`
- `monitoring/grafana/dashboards/wallet-service.json`, `wallet-runtime-and-database.json`, `wallet-business.json`
- `.dockerignore` (exclude `monitoring/` from the application image build, if not already excluded)

Must not edit: anything under `src/`, `pom.xml`, `Dockerfile`, `README.md`, `specs/` except ticking this file's subtasks.

## Success Criteria

Verified for real, with the stack running:

- Plain `docker compose up --build -d` starts two containers only.
- With `--profile monitoring`: Prometheus `/api/v1/targets` reports the application and the exporter `up`; Grafana `/api/health` is ok; `/api/search` lists the three dashboards; each datasource's health check passes.
- After sending a little traffic with curl (two wallets, a deposit, a withdrawal, a transfer, one rejection): every Prometheus expression used in a panel is accepted by the Prometheus API (no parse error), and those not waiting for task 12.0 return data; every SQL query of the business dashboard runs through `psql` as the read-only role and returns rows; both integrity queries return 0.
- The read-only role is refused an `INSERT` and an `UPDATE`.
- `docker compose --profile monitoring down -v` at the end; nothing left running.
