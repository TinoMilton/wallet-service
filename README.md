# Wallet Service

[![CI](https://github.com/TinoMilton/wallet-service/actions/workflows/ci.yml/badge.svg)](https://github.com/TinoMilton/wallet-service/actions/workflows/ci.yml)

A wallet microservice for the RecargaPay take-home assessment. It creates a wallet for a user and moves money in it: deposit, withdraw, and transfer to another wallet. Every operation appends to an immutable, per-wallet ledger, so any balance, current or at any past instant, can be explained from the record that produced it. Commands are synchronous: the caller knows immediately whether money moved. The assignment statement itself is not included in this public repository; this README and the [design guide](docs/design.md) summarise its requirements.

## What it is

A single Spring Boot service on PostgreSQL. The ledger is the source of truth: every deposit, withdrawal and transfer appends immutable entries carrying the balance after them, and the wallet row holds a cached balance that is updated in the same transaction. Idempotency keys make client retries safe, and a transactional outbox announces every operation. The service is stateless; PostgreSQL holds all state and all locking.

Stack: Java 25, Spring Boot 4.1, PostgreSQL 18, Flyway, JUnit 5 with Testcontainers and REST Assured, Prometheus and Grafana.

## Quick start

Needs Docker; running the tests from source also needs a JDK 25 or newer.

```bash
docker compose up --build                           # the application and PostgreSQL
curl http://localhost:8080/actuator/health/readiness # {"status":"UP"} when ready
./mvnw verify                                        # all tests, format check, 90% line / 85% branch coverage gate
```

With the stack up:

| URL | What |
|---|---|
| `http://localhost:8080/` | manual test console |
| `http://localhost:8080/swagger-ui/index.html` | interactive API documentation |
| `http://localhost:3000` | Grafana, after `docker compose --profile monitoring up -d` |

Ports, credentials and the other options are in [Running the service](docs/running.md).

## API at a glance

Base path `/api/v1`. Money-moving endpoints require an `Idempotency-Key` header. Errors are RFC 7807 problem details with a stable `code`.

| Method and path | Purpose |
|---|---|
| `POST /wallets` | Create a wallet for a user |
| `GET /wallets/{walletId}/balance` | Current balance |
| `GET /wallets/{walletId}/balance?at={ISO-8601 instant}` | Balance at a past instant |
| `GET /wallets/{walletId}/entries?page=&size=` | The wallet's ledger, newest first, paged |
| `POST /wallets/{walletId}/deposits` | Deposit |
| `POST /wallets/{walletId}/withdrawals` | Withdraw |
| `POST /transfers` | Transfer between two wallets |

Examples for every endpoint and the full error code list are in the [API guide](docs/api.md).

## Documentation index

| Guide | What it covers |
|---|---|
| [Running the service](docs/running.md) | Prerequisites, Docker Compose, environment variables, running the tests, coverage report, logs, the manual test console |
| [API](docs/api.md) | Endpoint table with status codes, a `curl` example and real response for each endpoint, idempotent replay, error codes |
| [Design](docs/design.md) | How each requirement is met (locking, historical balance, idempotency, availability, audit), design decisions and rejected alternatives |
| [Assumptions and trade-offs](docs/assumptions-and-trade-offs.md) | The assumptions behind the requirements, decisions made during the build, what was traded for the time budget and what comes next |
| [Testing](docs/testing.md) | One test per business rule (BR-01 to BR-29), concurrency tests, measured coverage, continuous integration |
| [Monitoring](docs/monitoring.md) | Prometheus and Grafana, the three dashboards with screenshots, the signals that matter, the k6 load generator |
| [Repository layout](docs/repository-layout.md) | What lives where |
| [Agent orchestration](docs/orchestration.md) | How the coding agents were coordinated |
| [Specifications](specs/) | PRD, tech spec (business rule catalogue in section 9.2), task breakdown |
