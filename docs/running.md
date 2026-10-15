# Running the service

[← Back to the README](../README.md)

Prerequisites:

- Docker (for running the stack and for the test suite's Testcontainers).
- To run the tests from source: a JDK 25 or newer. The build targets Java 25; it was verified on JDK 25 (in the Docker image) and JDK 26 (local). No Maven install is needed; the wrapper downloads it.

Run the whole system with one command:

```bash
docker compose up --build
```

The application image is built in two stages (JDK 25 build, JDK 25 JRE runtime, non-root user) and starts only after PostgreSQL is healthy. It is up when readiness reports `UP`:

```bash
curl http://localhost:8080/actuator/health/readiness
{"status":"UP"}
```

Host ports and database credentials come from environment variables with defaults, so nothing needs editing when a port or credential is taken:

| Variable | Default | Controls |
|---|---|---|
| `WALLET_APP_PORT` | `8080` | host port of the application |
| `WALLET_DB_PORT` | `5432` | host port of PostgreSQL |
| `POSTGRES_DB` | `wallet` | database name |
| `POSTGRES_USER` | `wallet` | database user |
| `POSTGRES_PASSWORD` | `wallet` | database password |

For example, when 8080 is taken: `WALLET_APP_PORT=8081 docker compose up --build`.

The database service also stands on its own for local development: `docker compose up -d postgres` starts just PostgreSQL, and the application's own defaults already point at `localhost:5432`.

## Run the tests

Run the tests (needs Docker; they use a real PostgreSQL through Testcontainers):

```bash
./mvnw verify
```

This runs the whole suite and fails the build below 90% line or 85% branch coverage. Where things are:

- Coverage report: `target/site/jacoco/index.html` after `./mvnw verify`.
- API documentation: interactive at `http://localhost:8080/swagger-ui/index.html`, raw OpenAPI at `http://localhost:8080/v3/api-docs`.
- Operation metrics: `http://localhost:8080/actuator/prometheus` (Prometheus format).
- Logs: one JSON line per event, carrying `requestId`, `walletId` and `operationId` (`docker compose logs app`).

## Manual test console

With the stack up, the application serves a manual test console at `http://localhost:8080/` (the `WALLET_APP_PORT` host port when it is set). It is a test tool, not part of the product API. From it you can create wallets, deposit, withdraw and transfer, read the current and a past balance, and page through a wallet's ledger, with every request logged together with its status, problem code and request id. Each money-moving form carries an `Idempotency-Key` and a "keep key" checkbox: with the key kept, sending the same deposit twice shows the replay return the original result, and changing the amount with the same key shows the `IDEMPOTENCY_KEY_REUSED` rejection. Choosing the same wallet as both ends of a transfer shows the `SAME_WALLET_TRANSFER` rejection.
