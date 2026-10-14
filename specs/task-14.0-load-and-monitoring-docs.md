# Task 14.0: Load Generator, Monitoring Documentation and End-to-end Check

<critical>Read AGENTS.md, techspec.md sections 6 and 8, the README, `docker-compose.yml` and everything under `monitoring/` before starting.</critical>

## Overview

Dashboards with no traffic show flat lines. Add a small load generator, document the monitoring stack, and prove the whole thing end to end with tasks 12.0 and 13.0 merged.

## Requirements

### Load generator

- `monitoring/load/wallet-traffic.js`, run by a `k6` service in `docker-compose.yml` under the profile `load` (pinned image version). It is started on demand: `docker compose --profile monitoring --profile load up k6` or the equivalent `run` command; choose one and document it.
- It creates a few dozen wallets, funds them, then for about three minutes at a modest rate (tens of requests per second, not a stress test) sends a mix of deposits, withdrawals, transfers and balance reads, plus a small share of: idempotent replays (same key, same request), reused keys (same key, different amount), withdrawals above the balance, and requests to an unknown wallet.
- Duration and rate come from environment variables with defaults.
- Amounts are written into the request body as literal text with two decimals, never computed through a floating-point number.
- The script is written for a human reader: small named functions with domain names.

### Documentation

- `README.md`: a "Monitoring" section after "Manual test console": the commands, the URLs, one short paragraph per dashboard, and a table of the signals that matter most and what a bad value means (p99, 5xx share, BUSY rate, pool pending, outbox age, the two integrity panels). State that the integrity queries scan whole tables and would run against a replica in production. Add `monitoring/` to the layout tree. Do not describe how any of it was written.
- `specs/techspec.md`: section 8 "Dashboards" bullet now says what is shipped; section 11 gets one line on the trade-off of Grafana reading the database directly.

### End-to-end check

Run the full stack with the load generator and fix whatever does not work in the dashboards, the Prometheus configuration or the compose file. Java code is out of scope: if a meter is wrong or missing, stop and describe it.

## Subtasks

- [x] 14.1 Load script and `k6` service
- [x] 14.2 End-to-end run and dashboard fixes
- [x] 14.3 README and tech spec

## Files

- `monitoring/load/wallet-traffic.js`
- `docker-compose.yml`
- `monitoring/` (fixes only)
- `README.md`
- `specs/techspec.md` (the two places named above)

Must not edit: anything under `src/`, `pom.xml`, `Dockerfile`, `specs/tasks.md`.

## Success Criteria

Verified for real:

- `./mvnw verify` exits 0 with the JaCoCo gate passing.
- With `--profile monitoring` up and the load run finished: every panel expression of the two Prometheus dashboards returns data through the Prometheus API, including p95 and p99, problems by code and replays by type; every SQL panel returns rows as the read-only role; both integrity queries return 0; k6 reports no unexpected status (every answer is one the script expected).
- A headless-browser screenshot of each of the three dashboards (tooling outside the repository, removed afterwards), saved to the directory the dispatch names, each showing rendered panels with data and no panel error.
- Every command written in the README was run as written.
- The stack is left running at the end, with the load finished.
