# Wallet Service

A wallet microservice for the RecargaPay take-home assessment. It creates a wallet for a user and moves money in it: deposit, withdraw, and transfer to another wallet. Every operation appends to an immutable, per-wallet ledger, so any balance, current or at any past instant, can be explained from the record that produced it. Commands are synchronous: the caller knows immediately whether money moved.

## 1. What it is

A single Spring Boot service on PostgreSQL. The ledger is the source of truth: every deposit, withdrawal and transfer appends immutable entries carrying the balance after them, and the wallet row holds a cached balance that is updated in the same transaction. Idempotency keys make client retries safe, and a transactional outbox announces every operation. The service is stateless; PostgreSQL holds all state and all locking.

## 2. Quick start

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

For example, when 8080 is taken: `WALLET_APP_PORT=9090 docker compose up --build`.

The database service also stands on its own for local development: `docker compose up -d postgres` starts just PostgreSQL, and the application's own defaults already point at `localhost:5432`.

### Manual test console

With the stack up, the application serves a manual test console at `http://localhost:8080/` (the `WALLET_APP_PORT` host port when it is set). It is a test tool, not part of the product API. From it you can create wallets, deposit, withdraw and transfer, read the current and a past balance, and page through a wallet's ledger, with every request logged together with its status, problem code and request id. Each money-moving form carries an `Idempotency-Key` and a "keep key" checkbox: with the key kept, sending the same deposit twice shows the replay return the original result, and changing the amount with the same key shows the `IDEMPOTENCY_KEY_REUSED` rejection. Choosing the same wallet as both ends of a transfer shows the `SAME_WALLET_TRANSFER` rejection.

### Monitoring

`docker compose --profile monitoring up -d` adds Prometheus and Grafana to the running stack. Prometheus scrapes the application and the PostgreSQL exporter every 5 seconds; Grafana is anonymous and read-only, with its home dashboard set to "Wallet — Business". The extra host ports come from the same environment variables as the rest of the stack: `WALLET_PROMETHEUS_PORT` (default 9090) and `WALLET_GRAFANA_PORT` (default 3000).

| URL | What |
|---|---|
| `http://localhost:3000/d/wallet-business` | Grafana: Wallet — Business (the home dashboard) |
| `http://localhost:3000/d/wallet-service` | Grafana: Wallet — Service |
| `http://localhost:3000/d/wallet-runtime-database` | Grafana: Wallet — Runtime and Database |
| `http://localhost:9090` | Prometheus: graph, targets and status |

**Wallet — Service** shows the API as a caller experiences it: request rate by endpoint, response time average, p50, p95 and p99, the share of 4xx and 5xx answers, money operations per second by type and outcome, operation duration percentiles, the `WALLET_BUSY` rate, problem answers by code, idempotent replays by type, and the outbox backlog and lag.

**Wallet — Runtime and Database** shows the machinery underneath: CPU and load, JVM heap and non-heap memory, garbage collection, live threads, the HikariCP connection pool, and PostgreSQL connections, commits, rollbacks, deadlocks, locks and database size.

**Wallet — Business** shows the money itself, straight from the database through the read-only `grafana_reader` role: the two ledger integrity checks, total money held, wallet counts and creation rate, volume moved and net flow over time, the average ticket per operation type, and the busiest wallets.

The signals that matter most:

| Signal | Dashboard | What a bad value means |
|---|---|---|
| p99 response time | Wallet — Service | The slowest 1% of requests is growing: wallets are queueing on their row lock, or the database is under pressure. |
| 5xx share | Wallet — Service | 500s are service failures; a rising share of 503 `WALLET_BUSY` means lock contention past the 2-second timeout. |
| BUSY rate | Wallet — Service | Requests waited too long for a wallet lock. Retrying with the same key is safe, but a sustained rate means one wallet is too hot. |
| Pool pending | Wallet — Runtime and Database | Requests are queueing for a database connection: the pool is saturated or the database has slowed down. |
| Outbox age | Wallet — Service | The oldest unpublished event is aging: event delivery is falling behind the operations. |
| Ledger integrity, transfer conservation | Wallet — Business | Above zero, a balance no longer matches its ledger entries or a transfer no longer conserves money. This is the audit invariant; investigate immediately. |

The two integrity queries scan whole tables, so they are fine for this small stack but would run against a replica in production, never against the primary.

The dashboards are flat without traffic, so a small load generator ships under the `load` compose profile (it never starts with `docker compose up` or `--profile monitoring` alone). With the stack up, run it on demand; it prints its summary and exits:

```bash
docker compose run --rm k6
```

It waits for readiness, creates and funds 30 wallets, then sends about 20 requests per second for 3 minutes: deposits, withdrawals, transfers and balance reads, plus a small share of idempotent replays, reused idempotency keys, withdrawals beyond the balance and requests for an unknown wallet. Length, pace and wallet count come from `WALLET_LOAD_DURATION` (default `3m`), `WALLET_LOAD_RATE` (default `20`) and `WALLET_LOAD_WALLETS` (default `30`).

Run the tests (needs Docker; they use a real PostgreSQL through Testcontainers):

```bash
./mvnw verify
```

This runs the whole suite and fails the build below 90% line or 85% branch coverage. Where things are:

- Coverage report: `target/site/jacoco/index.html` after `./mvnw verify`.
- API documentation: interactive at `http://localhost:8080/swagger-ui/index.html`, raw OpenAPI at `http://localhost:8080/v3/api-docs`.
- Operation metrics: `http://localhost:8080/actuator/prometheus` (Prometheus format).
- Logs: one JSON line per event, carrying `requestId`, `walletId` and `operationId` (`docker compose logs app`).

## 3. API

Base path `/api/v1`. Bodies are JSON. Money-moving endpoints require an `Idempotency-Key` header. Errors are RFC 7807 `application/problem+json` with a stable `code`.

| # | Method and path | Purpose | Success | Errors |
|---|---|---|---|---|
| 1 | `POST /wallets` | Create a wallet for a user | 201 | 400, 409 |
| 2 | `GET /wallets/{walletId}/balance` | Current balance | 200 | 404 |
| 3 | `GET /wallets/{walletId}/balance?at={ISO-8601 instant}` | Balance at a past instant | 200 | 400, 404 |
| 4 | `GET /wallets/{walletId}/entries?page=&size=` | The wallet's ledger, newest first, paged | 200 | 400, 404 |
| 5 | `POST /wallets/{walletId}/deposits` | Deposit | 201 | 400, 404, 422, 503 |
| 6 | `POST /wallets/{walletId}/withdrawals` | Withdraw | 201 | 400, 404, 422, 503 |
| 7 | `POST /transfers` | Transfer between two wallets | 201 | 400, 404, 422, 503 |

One example per endpoint, with the responses copied from the verification run against the Docker stack. Run them in order; each later command uses the ids the earlier ones created.

```bash
BASE=http://localhost:8080/api/v1
```

**1. Create a wallet.** A new wallet starts with a balance of zero. A user can have only one wallet; a second attempt returns 409.

```bash
curl -s -X POST $BASE/wallets -H 'Content-Type: application/json' -d '{"userId":"user-a"}'
```
```json
{"walletId":"bf41c8ee-8fc4-486d-afe0-616bfd9c6710","userId":"user-a","balance":0.00,"createdAt":"2026-10-11T18:30:02.236792886Z"}
```
```bash
# the walletId from the response above; this is the one from the verification run
WALLET_ID=bf41c8ee-8fc4-486d-afe0-616bfd9c6710
```

**2. Current balance.**

```bash
curl -s $BASE/wallets/$WALLET_ID/balance
```
```json
{"walletId":"bf41c8ee-8fc4-486d-afe0-616bfd9c6710","balance":35.00,"asOf":"2026-10-11T18:30:07.600566638Z"}
```

**3. Historical balance.** The instant must carry an offset and lie in the past. The result is the balance after the last ledger entry at or before it. Use any `createdAt` from an earlier response.

```bash
curl -s "$BASE/wallets/$WALLET_ID/balance?at=2026-10-11T18:30:02.789409136Z"
```
```json
{"walletId":"bf41c8ee-8fc4-486d-afe0-616bfd9c6710","balance":75.00,"asOf":"2026-10-11T18:30:02.789409136Z"}
```

**4. Ledger entries.** The wallet's audit trail, newest first. `page` starts at 0; `size` defaults to 20 and is capped at 100; the response reports the size the server applied.

```bash
curl -s "$BASE/wallets/$WALLET_ID/entries?page=0&size=10"
```
```json
{"walletId":"bf41c8ee-8fc4-486d-afe0-616bfd9c6710","page":0,"size":10,"totalEntries":3,"entries":[
  {"seq":3,"operationId":"002e8a43-eb04-44c1-a10a-17079221fe7a","type":"TRANSFER","amountSigned":-40.00,"balanceAfter":35.00,"createdAt":"2026-10-11T18:30:07.570988Z"},
  {"seq":2,"operationId":"e66553c4-b23b-434e-bd70-61fc78d743d3","type":"WITHDRAWAL","amountSigned":-25.00,"balanceAfter":75.00,"createdAt":"2026-10-11T18:30:02.789409Z"},
  {"seq":1,"operationId":"2c7c9836-3387-4725-af0e-e1627f83e6d7","type":"DEPOSIT","amountSigned":100.00,"balanceAfter":100.00,"createdAt":"2026-10-11T18:30:02.749634Z"}]}
```

**5. Deposit.** The `Idempotency-Key` header is required. Repeating the same request with the same key returns the original result and moves money once.

```bash
curl -s -X POST $BASE/wallets/$WALLET_ID/deposits -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: deposit-key-1' -d '{"amount":100.00}'
```
```json
{"operationId":"2c7c9836-3387-4725-af0e-e1627f83e6d7","type":"DEPOSIT","walletId":"bf41c8ee-8fc4-486d-afe0-616bfd9c6710","amount":100.00,"balance":100.00,"createdAt":"2026-10-11T18:30:02.749634469Z"}
```

**6. Withdraw.** A withdrawal greater than the balance is rejected (422) and changes nothing.

```bash
curl -s -X POST $BASE/wallets/$WALLET_ID/withdrawals -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: withdrawal-key-1' -d '{"amount":25.00}'
```
```json
{"operationId":"e66553c4-b23b-434e-bd70-61fc78d743d3","type":"WITHDRAWAL","walletId":"bf41c8ee-8fc4-486d-afe0-616bfd9c6710","amount":25.00,"balance":75.00,"createdAt":"2026-10-11T18:30:02.789409136Z"}
```

**7. Transfer.** Both wallets change in one transaction or neither does. The response returns the sender's resulting balance, because the caller acts for the sender. The ids below are the ones this run created; replace them with your own sender and receiver.

```bash
curl -s -X POST $BASE/transfers -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: transfer-key-1' -d '{"amount":40.00,"fromWalletId":"bf41c8ee-8fc4-486d-afe0-616bfd9c6710","toWalletId":"5ff78ebd-aac5-4a78-a0aa-56c2facae66f"}'
```
```json
{"operationId":"002e8a43-eb04-44c1-a10a-17079221fe7a","type":"TRANSFER","fromWalletId":"bf41c8ee-8fc4-486d-afe0-616bfd9c6710","toWalletId":"5ff78ebd-aac5-4a78-a0aa-56c2facae66f","amount":40.00,"fromBalance":35.00,"createdAt":"2026-10-11T18:30:07.570988472Z"}
```

Two behaviours worth seeing once. A withdrawal beyond the balance is rejected with a problem detail:

```bash
curl -s -X POST $BASE/wallets/$WALLET_ID/withdrawals -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: withdrawal-key-2' -d '{"amount":9999.00}'
```
```json
{"detail":"Wallet has insufficient funds","instance":"/api/v1/wallets/bf41c8ee-8fc4-486d-afe0-616bfd9c6710/withdrawals","status":422,"title":"Unprocessable Content","code":"INSUFFICIENT_FUNDS"}
```

Repeating the deposit of step 5 with the same key returns the original result, with the original operation id and balance, and credits nothing again:

```bash
curl -s -X POST $BASE/wallets/$WALLET_ID/deposits -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: deposit-key-1' -d '{"amount":100.00}'
```
```json
{"operationId":"2c7c9836-3387-4725-af0e-e1627f83e6d7","type":"DEPOSIT","walletId":"bf41c8ee-8fc4-486d-afe0-616bfd9c6710","amount":100.00,"balance":100.00,"createdAt":"2026-10-11T18:30:02.749634Z"}
```

### Error codes

Every error is an RFC 7807 problem detail with a stable `code`, produced in one place, `GlobalExceptionHandler`.

| Status | `code` | When |
|---|---|---|
| 400 | `INVALID_AMOUNT` | Amount is zero, negative, or has more than two decimal places |
| 400 | `MISSING_IDEMPOTENCY_KEY` | Header absent or blank on a money-moving endpoint |
| 400 | `INVALID_TIMESTAMP` | `at` is malformed, has no offset, or is in the future |
| 400 | `SAME_WALLET_TRANSFER` | Source and destination are the same wallet |
| 400 | `INVALID_REQUEST` | Body is missing, malformed, or fails validation (for example a blank `userId`, an `Idempotency-Key` longer than 128 characters, or an unparsable or negative `page`/`size`) |
| 404 | `WALLET_NOT_FOUND` | Unknown wallet id, or `at` is before the wallet was created |
| 404 | `ROUTE_NOT_FOUND` | No route matches the request |
| 405 | `METHOD_NOT_ALLOWED` | The route does not accept the request's method |
| 409 | `WALLET_ALREADY_EXISTS` | The user already has a wallet |
| 415 | `UNSUPPORTED_MEDIA_TYPE` | The media type of the request body is not supported |
| 422 | `INSUFFICIENT_FUNDS` | Withdrawal or transfer exceeds the balance |
| 422 | `IDEMPOTENCY_KEY_REUSED` | Key already used with a different request |
| 503 | `WALLET_BUSY` | Lock wait exceeded the timeout; safe to retry with the same key |
| 500 | `INTERNAL_ERROR` | Unexpected failure; no internal detail is exposed |

## 4. How the design meets the requirements

**Create wallet.** `WalletService.create` inserts the wallet with a zero balance; a unique constraint on `user_id` makes a second wallet for the same user a 409, detected by constraint name in `WalletService`.

**Retrieve balance.** The current balance is the cached `wallet.balance` column, read without a lock in `WalletService.currentBalance`. The cache is kept exact by the write flow (see below).

**Retrieve historical balance.** Every ledger entry stores `balance_after`, so a past balance is one indexed read: the last entry at or before the instant (`LedgerEntryRepository.lastEntryAtOrBefore`, using the `(wallet_id, created_at)` index). The instant is truncated to microseconds before comparing, because PostgreSQL stores microseconds and a finer cutoff could round up to a later entry (`WalletService.atMicrosecondPrecision`).

**Deposit and withdraw.** Each command is one database transaction in `LedgerService`: it takes a pessimistic lock on the wallet row (`WalletRepository.lockById`) with a 2-second `lock_timeout` (`SET LOCAL`, because PostgreSQL ignores the positive JPA lock-timeout hint), checks funds, appends one immutable ledger entry with the balance after it, and updates the cached balance in the same transaction. Rejections roll back, so nothing changes.

**Transfer.** Same flow, plus: both wallets are locked in ascending id order so two opposing transfers cannot deadlock (`LedgerService.lockWalletPair`), and the debit and credit are two ledger entries under one operation in one transaction. The balance check happens under the lock, so a concurrent withdrawal cannot make both sides still "succeed".

**Safe retries.** Every money-moving request carries an `Idempotency-Key`. The operation row with its unique key is inserted and flushed before any wallet lock is taken (`LedgerService.saveOperation`), so a concurrent duplicate waits on the unique index while holding no lock. A duplicate then replays in a fresh read-only transaction (`LedgerService.replay`) and returns the original result; the same key with a different request is rejected by comparing the stored `RequestFingerprint`.

**Mission-critical (availability).** The application is stateless: no in-process state, no local locks, so several instances can run side by side. Readiness includes the database (`application.yml`), shutdown is graceful, the connection pool is bounded, and a request that waits too long on a wallet lock fails with a retryable 503 rather than hanging. What is not demonstrated here is described in section 7.

**Full traceability (audit).** The append-only ledger is the audit trail: entries are immutable, enforced by a database trigger (`V2__ledger_entry_immutable.sql`) and by entities with no setters marked `@Immutable`. Each entry links to the operation that caused it, and `GET /entries` exposes the chain. Every operation is announced through a transactional outbox written in the same transaction (`OutboxPublisher`), every operation is logged once with `requestId`, `walletId` and `operationId` (`OperationMetrics`, `RequestIdFilter`), and operation outcomes are counted in Prometheus metrics.

## 5. Design decisions and rejected alternatives

These are the decisions from `specs/techspec.md` section 2, with the reasons. The full argument for each is in the tech spec.

- **Java 25 LTS, not Java 27.** Java 27 is a non-LTS release with a short support window, outside the range Spring Boot 4.1 documents. Nothing in a wallet service needs it.
- **Synchronous commands, not async (202 + poll).** Deposits and withdrawals must confirm their outcome to the caller. Asynchrony would bring eventual consistency into the most consistency-critical path. Asynchrony is kept for event publication only, via the outbox.
- **Ledger as the source of truth, not CQRS or event sourcing.** The historical-balance and audit requirements are properties of an immutable chain of entries. A separate read model would add projection lag and double the test surface for no new guarantee.
- **Ledger first, not a balance column plus a side audit log.** Nothing would tie the balance to the log, so an auditor could observe a balance but not prove it.
- **Pessimistic row locks, not optimistic `@Version`.** A version conflict fails a legitimate request; under contention on a busy wallet, incoming deposits would be rejected for no business reason. With row locks, concurrent requests queue for milliseconds instead.
- **Single-entry ledger, not full double-entry bookkeeping (deferred, not rejected).** Double entry needs system and external accounts, and the assignment has no counterparty for deposits and withdrawals. It is the natural production evolution.
- **PostgreSQL, not MongoDB.** Transfers need multi-row ACID transactions and row-level locks.
- **Transactional outbox, not Kafka (for this build).** A broker and consumers are excess infrastructure for one service in this time budget. The outbox is written in the same transaction as the money movement, so there is no dual write; the relay target is one class to replace.
- **One app and one PostgreSQL in compose, not a multi-instance HA setup.** The time went to the outbox instead. The application is stateless, so scaling it is a deployment change; database failover is described, not built (section 7).

## 6. Assumptions

The assumptions from `specs/techspec.md` section 10, each with its rationale, plus the ones decided during the build.

1. A user is an opaque `userId` owned by another system, not validated here. The assignment defines no user model; identity belongs outside a wallet microservice.
2. Callers are trusted upstream services; authentication and authorisation are out of scope. The assignment does not mention security, and adding it would displace the core requirements.
3. A user has one wallet. "A user's wallet" is singular throughout the assignment; wallets are addressed by id, so the constraint can be relaxed later without breaking callers.
4. One currency, BRL, and it is not stored. The assignment mentions no currencies, and RecargaPay operates in Brazil.
5. Amounts have at most two decimal places and must be positive; anything else is rejected, never rounded. Silent rounding would make the caller's books disagree with the ledger.
6. A balance can never be negative. No credit or overdraft is mentioned.
7. A deposit is a plain top-up and a withdrawal a plain removal, with no external payment provider. The assignment models wallets only.
8. "A specific point in the past" means the balance after the last operation recorded at or before that instant. It is the reading an auditor would expect, and stable once the instant has passed.
9. A historical query for a future instant is invalid. Its answer could still change, so it is not a historical balance.
10. A historical query for an instant before the wallet existed returns 404. There was no wallet to ask about, which is different from a zero balance.
11. Timestamps require an explicit offset and are stored in UTC. It removes ambiguity about the caller's time zone.
12. Clients generate idempotency keys and keep them stable across retries. Only the client knows that two requests are the same intent.

Decided during the build (recorded in `specs/tasks.md`):

- **The operation row is inserted and flushed before the wallet lock is taken.** A duplicate idempotency key then waits on the unique index holding no wallet lock, so it cannot deadlock with the original or block other requests on that wallet.
- **The lock wait is `SET LOCAL lock_timeout = '2s'`, issued by the service.** The JPA lock-timeout hint is ignored for positive values on PostgreSQL; this was verified, not assumed.
- **Ledger timestamps are nudged forward by one microsecond when the clock repeats an instant already stored.** PostgreSQL keeps microseconds, so sequence order and timestamp order always agree even when the injected clock stands still.
- **A cutoff with sub-microsecond precision is truncated, not rounded.** A nanosecond-below cutoff would round up to the stored microsecond and wrongly include that entry.
- **The transfer response returns only the sender's resulting balance.** The caller acts for the sender.
- **The ledger page defaults to 20 entries, capped at 100, page numbered from 0, and reports the size actually applied.** Generous for inspection, bounded for the database.
- **The outbox publisher runs on a fixed delay of 2 seconds, in batches of 50, selecting with `FOR UPDATE SKIP LOCKED`.** Deterministic for one instance, safe for several. Tests switch scheduling off and drive a publishing run directly.
- **Only successful operations emit events; wallet creation does not.** A rejected operation moved no money and has nothing to announce.
- **Idempotency keys are retained indefinitely.** The operation row doubles as the operation record for audit; partitioning by time is the scale-out path.
- **The build targets Java 25 bytecode.** The machine it was written on had only JDK 26, so local builds compiled with `release=25` on JDK 26, and the Docker image builds and runs on JDK 25.

## 7. Trade-offs and compromises

What was traded for the time budget, plainly. Nothing was cut: every feature in the assignment and every business rule in the tech spec is built and tested.

- **High availability is described, not demonstrated.** The compose setup runs one PostgreSQL; it is a single point of failure. The application is stateless and holds no locks in memory, so running more instances behind a load balancer is a deployment change, and readiness already includes the database. Next: managed PostgreSQL with a synchronous standby and automatic failover, PgBouncer at higher instance counts.
- **No broker.** Outbox events are relayed to a structured log line (`LoggingEventRelay`), at-least-once, with the operation id for deduplication. Next: replace that one class with a broker client; the command path does not change.
- **Single currency.** No multi-currency wallets or conversion. Next: a currency per wallet and rejection of cross-currency transfers.
- **Single-entry ledger.** Money entering and leaving the system has no counterparty account, so the ledger is self-consistent but not double-entry. Next: system and external accounts on top of the same entries.
- **Writes to one wallet are serialised by its row lock.** A very busy wallet is limited to one write at a time. Transactions are short and the lock timeout bounds waiting. Next, for a hot wallet: a different model, such as batched settlement.
- **Amounts are JSON numbers.** Clients that parse numbers as floating point can lose precision; the server parses into `BigDecimal` and rejects extra decimals. String amounts would remove the risk.
- **404 is returned both for an unknown wallet and for an instant before wallet creation.** A distinct code could be added without a breaking change; accepted for simplicity.
- **No authentication.** Callers are assumed to be trusted platform services (assumption 2). Next: platform authentication in front of the service.
- **Idempotency keys and operations are retained indefinitely**, so the tables grow with every operation. They are also the audit record; partition by time at scale.

## 8. Testing

The rule: **every business rule has its own test.** Each test carries its rule id in its display name, verifies one behaviour, and can fail for one reason only. The catalogue lives in `specs/techspec.md` section 9.2; this is where each rule is proven.

| Rule | Test class |
|---|---|
| BR-01 new wallet has zero balance | `CreateWalletTest`, `CurrentBalanceTest` |
| BR-02 one wallet per user | `CreateWalletTest` |
| BR-03 amount must be positive | `MoneyTest`, `DepositTest`, `TransferTest` |
| BR-04 at most two decimals, never rounded | `MoneyTest`, `DepositTest` |
| BR-05 deposit adds exactly the amount | `DepositTest` |
| BR-06 withdrawal removes exactly the amount | `WithdrawalTest` |
| BR-07 overdraw rejected, balance unchanged | `WithdrawalTest` |
| BR-08 transfer debits and credits equally | `TransferTest` |
| BR-09 transfer beyond balance rejected, nothing changes | `TransferTest` |
| BR-10 transfer to self rejected | `TransferTest` |
| BR-11 unknown wallet rejected on every endpoint | `CurrentBalanceTest`, `DepositTest`, `WithdrawalTest`, `TransferTest`, `LedgerEntriesTest`, `HistoricalBalanceTest` |
| BR-12 current balance equals last operation's result | `DepositTest` |
| BR-13 balance at T is the last entry at or before T | `HistoricalBalanceTest` |
| BR-14 existing wallet with no operations had zero | `HistoricalBalanceTest` |
| BR-15 instant before creation rejected | `HistoricalBalanceTest` |
| BR-16 future, malformed or offset-less instant rejected | `HistoricalBalanceTest` |
| BR-17 each operation records the right entries | `DepositTest`, `TransferTest` |
| BR-18 ledger entries cannot be updated or deleted | `LedgerImmutabilityTest` |
| BR-19 cached balance = last entry = sum of entries | `DepositTest`, `AuditInvariantRandomisedTest`, and every `ConcurrencyTest` |
| BR-20 money-moving without a key rejected | `WithdrawalTest`, `DepositTest`, `TransferTest` |
| BR-21 same key returns the original result | `IdempotentReplayTest` |
| BR-22 key reuse with a different request rejected | `IdempotentReplayTest` |
| BR-23 retry after rejection is evaluated again | `IdempotentReplayTest` |
| BR-24 concurrent withdrawals never overspend | `ConcurrencyTest` |
| BR-25 opposing transfers never deadlock | `ConcurrencyTest` |
| BR-26 concurrent same-key requests produce one operation | `ConcurrencyTest` |
| BR-27 lock timeout is a 503 and moves no money | `WalletBusyTest` |
| BR-28 one event per success, none per rejection | `OutboxEventTest` |
| BR-29 concurrent deposits all apply | `ConcurrencyTest` |

Unit tests cover the money value object and the request fingerprint (`MoneyTest`, `RequestFingerprintsTest`); everything else runs through HTTP with REST Assured against a real PostgreSQL 18 in Testcontainers. The database is never mocked. The suite shares one Spring context and one container for speed.

**The concurrency tests** (`ConcurrencyTest`, covering BR-24 to BR-26 and BR-29) run 100 simultaneous callers, each on its own virtual thread, released at the same instant by a latch (`support/ConcurrentCallers.runConcurrently`). The application under test runs with virtual threads enabled, the same as production. The pool holds 10 connections, so the callers really do queue on connections and on the wallet row lock. Each test asserts the full set of responses and the final state, and ends by checking the audit invariant (BR-19) on every wallet involved.

**Measured totals from the final run** (after the last code change): 127 tests, all passing; 96.39% line and 87.50% branch coverage, above the 90% / 85% build gate; `ConcurrencyTest` passed 5 consecutive runs.

## 9. Time spent

The implementation commits span 2026-10-11, from the first commit at 12:16 (UTC-3) to the last at 15:36 (UTC-3), an elapsed time of 3 hours 20 minutes, measured with `git log` on the main branch. Design and specification time, before the first commit, is reported separately by the author.

## 10. Repository layout

```
├── Dockerfile                  multi-stage build: JDK 25 build, JDK 25 JRE runtime, non-root
├── docker-compose.yml          the application and PostgreSQL, with health checks; monitoring and load profiles
├── monitoring/                 Prometheus config, Grafana provisioning and dashboards, the k6 load script
├── pom.xml                     Spring Boot 4.1.1, JaCoCo gate, Spotless
├── src/main/java/com/recargapay/wallet/
│   ├── api/                    controllers, DTOs, GlobalExceptionHandler
│   ├── config/                 clock, Jackson, OpenAPI, scheduling
│   ├── domain/                 entities, Money, WalletId, IdempotencyKey, RequestFingerprint, exceptions
│   ├── observability/          operation metrics and outcome levels, request id filter
│   ├── outbox/                 publisher, relay, event payloads, gauges
│   ├── repository/             Spring Data JPA repositories
│   └── service/                WalletService, LedgerService, IdempotentCommandExecutor, RequestFingerprints
├── src/main/resources/
│   ├── application.yml         datasource, virtual threads, actuator, outbox settings
│   ├── db/migration/           Flyway migrations (schema, immutability trigger)
│   └── static/index.html       the manual test console served at /
├── src/test/java/com/recargapay/wallet/    tests by concern, shared helpers in support/
└── specs/                      prd.md, techspec.md, tasks.md, task files
```
