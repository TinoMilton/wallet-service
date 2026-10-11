# Tech Spec — Wallet Service (RecargaPay Challenge)

Source: `specs/Wallet_Service_Assignment.pdf`

## 1. Executive Summary

The Wallet Service is a single Spring Boot microservice on PostgreSQL that creates wallets and moves money through deposits, withdrawals and transfers. Every command is synchronous and runs in one database transaction, so a caller knows immediately whether money moved.

The design rests on four decisions:

- **An append-only ledger is the source of truth.** Each wallet has an immutable chain of ledger entries, and each entry stores the balance after it. The balance on the wallet row is a cache updated in the same transaction. This one mechanism provides the historical balance and the audit trail.
- **Pessimistic row locking guarantees correctness.** Every write locks the wallet row; transfers lock both wallets in a fixed order. Concurrent requests queue for milliseconds instead of failing.
- **Idempotency keys are mandatory on money-moving endpoints.** A database unique constraint makes a retried request return the original result instead of moving money twice.
- **A transactional outbox publishes events asynchronously.** The event is written in the same transaction as the ledger entries, so there is no dual-write problem and no asynchrony on the command path.

One principle governs how all of it is built: **the code is written for humans to read, not for machines.** Every business rule is listed by id and covered by its own readable, independent test (section 9).

## 2. Decisions Challenged

Each of these was considered and decided against. They are recorded here because the reasons are part of the design.

| Alternative | Decision | Reason |
|---|---|---|
| Java 27 | Rejected for Java 25 LTS | Java 27 is a non-LTS release (GA 2026-09-15, updates end March 2027) and sits outside the Java range Spring Boot 4.1 documents as compatible. It adds nothing a wallet service needs. |
| Asynchronous commands (202 and poll) | Rejected | Deposits and withdrawals must confirm their outcome to the caller. Async commands bring eventual consistency, ordering and reconciliation problems into the most consistency-critical path. Asynchrony is kept for event publication only. |
| CQRS and event sourcing | Rejected | The historical-balance and audit requirements are ledger properties. A separate read model would add projection lag (a user not seeing their own deposit) and double the implementation and test surface. |
| Balance column as the truth, with a side audit log | Rejected | Nothing ties the balance to the log, so an auditor can observe a balance but not prove it. Historical queries would depend on mutable state or scan full history. |
| Optimistic locking (`@Version`) | Rejected | A version conflict fails a legitimate request. Under contention on a busy wallet, incoming deposits would be rejected for no business reason. It also still needs a fixed update order to avoid deadlocks on transfers. |
| Full double-entry bookkeeping | Deferred | It needs system and external accounts, and the assignment has no counterparty for deposits and withdrawals. It is the production evolution of this ledger. |
| Multiple instances and a Postgres replica in docker-compose | Not built | The time went to the outbox instead. Multi-instance deployment and database failover are documented in section 8. |
| MongoDB | Rejected | Transfers need multi-row ACID transactions and row-level locks. |
| Kafka | Rejected for this build | A broker and consumers are excess infrastructure for one service in this time budget. The outbox can feed a broker later without changing the command path. |

## 3. Stack

| Layer | Choice | Why |
|---|---|---|
| Language | Java 25 (LTS) | Long support window and inside Spring Boot 4.1's documented range |
| Framework | Spring Boot 4.1.x, Spring Web MVC, Bean Validation | Mature, and what reviewers expect |
| Concurrency model | Virtual threads with blocking code | High request concurrency without a reactive stack; a thread waiting on a row lock simply parks |
| Persistence | PostgreSQL, Spring Data JPA, Flyway | ACID transactions, row-level locking, versioned schema |
| Eventing | Transactional outbox table and scheduled publisher | Events without a dual write |
| API docs | springdoc-openapi | The contract is visible and executable for reviewers |
| Observability | Spring Boot Actuator, Micrometer (Prometheus format), JSON logs | Supports the traceability requirement |
| Tests | JUnit 5, Testcontainers (PostgreSQL), REST Assured, JaCoCo | Tests run against the real database engine; coverage of the whole application is measured and gated |
| Packaging | Docker and docker-compose (one app, one Postgres) | `docker compose up` gives a working system |

The exact Spring Boot 4.1.x patch version is confirmed when the project is scaffolded.

## 4. System Architecture

### Component Overview

All components are new; the repository contains no code yet.

| Component | Responsibility |
|---|---|
| `WalletController`, `TransferController` | HTTP mapping, request validation, idempotency header extraction |
| `IdempotentCommandExecutor` | Runs a command, and on a duplicate idempotency key loads and returns the original operation in a new transaction |
| `WalletService` | Wallet creation and balance queries (current and historical) |
| `LedgerService` | Deposit, withdrawal and transfer: locks wallets, checks funds, appends entries, updates the cached balance, writes the outbox event |
| `Money` | Value object for amounts: scale 2, positive, never rounded |
| `Balance` | Value object for what a wallet holds: scale 2, zero or positive. Separate from `Money` because an operation amount can never be zero and a balance can |
| `Clock` (injected) | The only source of time in the application, so tests can control it |
| `WalletRepository`, `OperationRepository`, `LedgerEntryRepository`, `OutboxEventRepository` | Spring Data JPA repositories |
| `OutboxPublisher` | Scheduled job that relays unpublished events and marks them published |
| `GlobalExceptionHandler` | Maps domain errors to RFC 7807 problem details |

### Write Flow (deposit, withdrawal, transfer)

1. The controller validates the body and the `Idempotency-Key` header, and builds a `Money`.
2. `IdempotentCommandExecutor` calls `LedgerService` inside one `@Transactional` boundary.
3. `LedgerService` sets `lock_timeout` for the transaction and locks the wallet row or rows with `SELECT ... FOR UPDATE`, in ascending `wallet_id` order.
4. It inserts the `operation` row with the idempotency key and request fingerprint.
5. For each wallet it checks funds, computes the next `seq` and `balance_after`, inserts a `ledger_entry` and updates `wallet.balance`.
6. It inserts one `outbox_event`.
7. The transaction commits and the controller returns 201.

If step 4 fails on the unique constraint, the transaction rolls back and the executor handles the replay in a new transaction (section 7).

### Read Flow

- Current balance reads `wallet.balance`.
- Historical balance reads one ledger entry through the `(wallet_id, created_at)` index.
- Neither read takes a lock.

## 5. Data Model

```sql
wallet (
  id          UUID PRIMARY KEY,
  user_id     VARCHAR(64) NOT NULL UNIQUE,
  balance     NUMERIC(19,2) NOT NULL CHECK (balance >= 0),
  created_at  TIMESTAMPTZ NOT NULL
)

operation (
  id                   UUID PRIMARY KEY,
  type                 VARCHAR(16) NOT NULL,   -- DEPOSIT | WITHDRAWAL | TRANSFER
  idempotency_key      VARCHAR(128) NOT NULL UNIQUE,
  request_fingerprint  CHAR(64) NOT NULL,      -- SHA-256 of type, wallet ids and amount
  created_at           TIMESTAMPTZ NOT NULL
)

ledger_entry (
  id             UUID PRIMARY KEY,
  operation_id   UUID NOT NULL REFERENCES operation(id),
  wallet_id      UUID NOT NULL REFERENCES wallet(id),
  seq            BIGINT NOT NULL,              -- per wallet, starts at 1
  amount_signed  NUMERIC(19,2) NOT NULL CHECK (amount_signed <> 0),
  balance_after  NUMERIC(19,2) NOT NULL CHECK (balance_after >= 0),
  created_at     TIMESTAMPTZ NOT NULL,
  UNIQUE (wallet_id, seq)
)
INDEX ledger_entry (wallet_id, created_at)

outbox_event (
  id              UUID PRIMARY KEY,
  aggregate_type  VARCHAR(32) NOT NULL,        -- OPERATION
  aggregate_id    UUID NOT NULL,               -- operation id
  event_type      VARCHAR(32) NOT NULL,        -- FundsDeposited | FundsWithdrawn | FundsTransferred
  payload         JSONB NOT NULL,
  occurred_at     TIMESTAMPTZ NOT NULL,
  published_at    TIMESTAMPTZ NULL
)
INDEX outbox_event (occurred_at) WHERE published_at IS NULL
```

### Invariants

1. Ledger entries are never updated or deleted. A database trigger on `ledger_entry` rejects `UPDATE` and `DELETE`.
2. `seq` and `created_at` are assigned while the wallet row is locked, so the two orderings always agree for a wallet.
3. A deposit or withdrawal writes one entry. A transfer writes two entries (negative for the sender, positive for the receiver) under one operation.
4. Every operation writes exactly one outbox event in the same transaction.
5. For every wallet: `wallet.balance = balance_after of the last entry = SUM(amount_signed)`. A wallet with no entries has balance zero.

### Queries

- **Current balance:** `SELECT balance FROM wallet WHERE id = ?`
- **Historical balance at T:** `SELECT balance_after FROM ledger_entry WHERE wallet_id = ? AND created_at <= ? ORDER BY seq DESC LIMIT 1`. No row, for a wallet that existed at T, means zero.

## 6. API Design

Base path: `/api/v1`. Bodies are JSON. Errors are RFC 7807 `application/problem+json` with a stable `code` field.

| Method and path | Purpose | Success | Errors |
|---|---|---|---|
| `POST /wallets` | Create a wallet for a user | 201 | 400, 409 |
| `GET /wallets/{walletId}/balance` | Current balance | 200 | 404 |
| `GET /wallets/{walletId}/balance?at={ISO-8601}` | Balance at a past instant | 200 | 400, 404 |
| `GET /wallets/{walletId}/entries?page=&size=` | Paged ledger, newest first | 200 | 404 |
| `POST /wallets/{walletId}/deposits` | Deposit | 201 | 400, 404, 422, 503 |
| `POST /wallets/{walletId}/withdrawals` | Withdraw | 201 | 400, 404, 422, 503 |
| `POST /transfers` | Transfer between wallets | 201 | 400, 404, 422, 503 |

### Request and Response Shapes

```jsonc
// POST /wallets
{ "userId": "user-123" }
// 201
{ "walletId": "…", "userId": "user-123", "balance": 0.00, "createdAt": "…" }

// GET /wallets/{walletId}/balance[?at=…]
{ "walletId": "…", "balance": 150.25, "asOf": "2026-10-11T14:03:22Z" }

// POST /wallets/{walletId}/deposits   and   /withdrawals
// Header: Idempotency-Key: <client-generated, up to 128 chars>
{ "amount": 50.00 }
// 201
{ "operationId": "…", "type": "DEPOSIT", "walletId": "…", "amount": 50.00,
  "balance": 200.25, "createdAt": "…" }

// POST /transfers
// Header: Idempotency-Key: <…>
{ "fromWalletId": "…", "toWalletId": "…", "amount": 25.00 }
// 201
{ "operationId": "…", "type": "TRANSFER", "fromWalletId": "…", "toWalletId": "…",
  "amount": 25.00, "fromBalance": 175.25, "createdAt": "…" }
```

The transfer response returns only the sender's resulting balance, because the caller acts for the sender.

### Error Codes

| Status | `code` | When |
|---|---|---|
| 400 | `INVALID_AMOUNT` | Amount is zero, negative, or has more than two decimal places |
| 400 | `MISSING_IDEMPOTENCY_KEY` | Header absent on a money-moving endpoint |
| 400 | `INVALID_TIMESTAMP` | `at` is malformed, has no offset, or is in the future |
| 400 | `SAME_WALLET_TRANSFER` | Source and destination are the same wallet |
| 400 | `INVALID_REQUEST` | Body is missing, malformed, or fails validation (for example a blank `userId`) |
| 404 | `WALLET_NOT_FOUND` | Unknown wallet id, or `at` is before the wallet was created |
| 409 | `WALLET_ALREADY_EXISTS` | The user already has a wallet |
| 422 | `INSUFFICIENT_FUNDS` | Withdrawal or transfer exceeds the balance |
| 422 | `IDEMPOTENCY_KEY_REUSED` | Key already used with a different request |
| 503 | `WALLET_BUSY` | Lock wait exceeded the timeout; safe to retry with the same key |
| 500 | `INTERNAL_ERROR` | Unexpected failure; no internal detail is exposed |

### Main Interfaces

```java
public interface WalletService {
    Wallet create(String userId);
    Balance currentBalance(WalletId walletId);
    Balance balanceAt(WalletId walletId, Instant at);
    Page<LedgerEntry> entries(WalletId walletId, Pageable pageable);
}

public interface LedgerService {
    OperationResult deposit(WalletId walletId, Money amount, IdempotencyKey key);
    OperationResult withdraw(WalletId walletId, Money amount, IdempotencyKey key);
    OperationResult transfer(WalletId from, WalletId to, Money amount, IdempotencyKey key);
}
```

Services take and return domain types only. Controllers convert path and body values into domain types, and map results to response DTOs; a service never depends on an API type.

## 7. Consistency, Concurrency and Traceability

### Atomicity

Each command is one database transaction. A transfer debits one wallet and credits the other in that single transaction, so a partial transfer cannot exist.

### Concurrency

- Every write locks the wallet row with `SELECT ... FOR UPDATE` before reading its balance.
- A transfer locks both wallets in ascending `wallet_id` order. Two opposing transfers therefore request locks in the same order and cannot deadlock.
- The transaction sets `SET LOCAL lock_timeout` (2 seconds). A request that waits longer fails with 503 `WALLET_BUSY` and can be retried safely with the same idempotency key.
- There is no retry loop and no version column.

### Idempotency

- `Idempotency-Key` is required on deposit, withdrawal and transfer.
- The key is stored on the `operation` row under a global unique constraint and inserted in the same transaction as the ledger entries. The constraint is the guard: of two concurrent requests with the same key, the second insert waits for the first to commit and then fails as a duplicate.
- On a duplicate, the failed transaction rolls back. The executor then loads the original operation in a new transaction and compares fingerprints:
  - Same fingerprint: return the original result with 201.
  - Different fingerprint: return 422 `IDEMPOTENCY_KEY_REUSED`.
- Only successful operations are stored. A retry after a business failure such as `INSUFFICIENT_FUNDS` is evaluated again.
- Wallet creation needs no key. The unique constraint on `user_id` makes a retry return 409.

### Traceability

- The ledger is the audit trail. Any balance, current or past, is explained by the chain of entries up to it, and each entry links to the operation that caused it.
- Immutability is enforced in the database by trigger and in the application by entities with no setters, marked `@Immutable`.
- `GET /wallets/{walletId}/entries` exposes the chain for audit.
- Every log line and outbox payload carries the operation id.

### Outbox

- `OutboxPublisher` runs on a fixed delay, selects unpublished events in `occurred_at` order with `FOR UPDATE SKIP LOCKED`, relays them and sets `published_at`.
- In this build the relay target is a structured log line. In production it would be a message broker.
- Delivery is at-least-once. Events carry the operation id so consumers can deduplicate.
- Only money operations emit events; wallet creation does not.

### JPA Rules

- The locking query is the first read of a wallet in the transaction, so the persistence context cannot return a stale instance.
- `spring.jpa.open-in-view` is `false`.
- `spring.jpa.hibernate.ddl-auto` is `validate`; Flyway is the only owner of the schema.
- To verify during implementation: how the JPA lock-timeout hint behaves on PostgreSQL. `SET LOCAL lock_timeout` is the planned mechanism because the hint is not expected to give a millisecond wait there.

## 8. Non-functional Posture

### Availability

Built:

- The application is stateless; all state and all locking live in PostgreSQL, so any number of instances can run side by side.
- Liveness and readiness probes through Actuator; readiness includes the database.
- Graceful shutdown, so in-flight requests complete before the process exits.
- Request and lock timeouts, and a bounded HikariCP pool, so a slow database cannot exhaust the service.

Documented, not built:

- Several instances behind a load balancer.
- Managed PostgreSQL with a synchronous standby and automatic failover.
- PgBouncer for connection pooling at higher instance counts.

### Monitoring and Observability

- **Metrics** (Prometheus format at `/actuator/prometheus`):
  - `wallet_operations_total{type,outcome}`
  - `wallet_operation_duration_seconds{type}`
  - `wallet_outbox_unpublished` (gauge) and `wallet_outbox_lag_seconds`
  - Standard HTTP, JVM and HikariCP metrics
- **Logs:** JSON, one line per event, with `requestId`, `walletId` and `operationId`. Business rejections log at INFO, lock timeouts at WARN, unexpected failures at ERROR.
- **Dashboards:** none are shipped. The metrics are named so a Grafana dashboard can be built on them.

### Integration Points

There are none. The service calls no external systems. It assumes its callers are trusted upstream services (section 10).

## 9. Code Standards and Testing Approach

### 9.1 Guiding Principle: Code Is Written for Humans

**All code in this project, production and test, is written to be read by a person.** The compiler accepts anything correct; a reviewer, an auditor or the next engineer has to understand it. In a service that moves money, code that cannot be read cannot be trusted. Where readability and brevity or cleverness conflict, readability wins.

This applies to every file:

- **Names come from the domain.** `wallet`, `deposit`, `ledgerEntry`, `insufficientFunds`; not `data`, `process`, `handler2`, `flag`.
- **A method does one thing at one level of abstraction.** `LedgerService.transfer` reads as the steps in section 4: lock the wallets, check funds, append the entries, update the balances, record the event. Each step is a named method.
- **Domain types replace primitives.** `Money`, `WalletId` and `IdempotencyKey` appear in signatures instead of `BigDecimal`, `UUID` and `String`, so a signature explains itself and arguments cannot be swapped.
- **Each business rule lives in one place** and fails with an exception named after the rule (`InsufficientFundsException`, `SameWalletTransferException`).
- **No cleverness.** No nested ternaries, no dense stream chains, no reflection tricks, no abstraction added for a future that has not arrived.
- **Comments explain why, never what.** If a comment is needed to explain what the code does, the code is rewritten.
- **Formatting is uniform**, applied by one formatter configuration in the build, so no reader has to adjust to a personal style.

### 9.2 Business Rules and Coverage

**Every business rule below has at least one dedicated test, and the target is 100% of rules covered.** The measure is rule coverage, not line coverage: a line can be executed by a test that asserts nothing about it, but a rule is covered only when a test fails if the rule is broken.

Each test carries its rule id in its display name, so the mapping between this table and the test report can be checked by reading.

| Id | Business rule | Level |
|---|---|---|
| BR-01 | A new wallet starts with a balance of zero. | Integration |
| BR-02 | A user can have only one wallet; a second creation is rejected (409). | Integration |
| BR-03 | An amount must be greater than zero (400). | Unit |
| BR-04 | An amount may have at most two decimal places and is never rounded (400). | Unit |
| BR-05 | A deposit increases the balance by exactly the amount. | Integration |
| BR-06 | A withdrawal decreases the balance by exactly the amount. | Integration |
| BR-07 | A withdrawal greater than the balance is rejected (422) and the balance is unchanged. | Integration |
| BR-08 | A transfer debits the sender and credits the receiver by the same amount. | Integration |
| BR-09 | A transfer greater than the sender's balance is rejected (422) and neither wallet changes. | Integration |
| BR-10 | A transfer from a wallet to itself is rejected (400). | Integration |
| BR-11 | Any operation on an unknown wallet is rejected (404); for a transfer, on either side. | Integration |
| BR-12 | The current balance equals the balance after the most recent operation. | Integration |
| BR-13 | The historical balance at T is the balance after the last operation at or before T, with T inclusive. | Integration |
| BR-14 | The historical balance of an existing wallet with no operations up to T is zero. | Integration |
| BR-15 | A historical query for an instant before the wallet was created is rejected (404). | Integration |
| BR-16 | A historical query with a future, malformed or offset-less timestamp is rejected (400). | Integration |
| BR-17 | Every operation records one ledger entry per affected wallet, with the correct amount and balance after. | Integration |
| BR-18 | A ledger entry cannot be updated or deleted. | Integration |
| BR-19 | For every wallet, cached balance, last entry's balance and sum of entries are equal. | Integration |
| BR-20 | A money-moving request without an idempotency key is rejected (400). | Integration |
| BR-21 | Repeating a request with the same key returns the original result and moves money once. | Integration |
| BR-22 | Reusing a key with a different request is rejected (422). | Integration |
| BR-23 | A retry after a rejected operation is evaluated again. | Integration |
| BR-24 | Concurrent withdrawals never overspend: of N for the full balance, exactly one succeeds. | Integration |
| BR-25 | Opposing concurrent transfers complete without deadlock and conserve the total. | Integration |
| BR-26 | The same idempotency key sent concurrently produces one operation. | Integration |
| BR-27 | A request that cannot obtain the wallet lock in time is rejected (503) and moves no money. | Integration |
| BR-28 | A successful operation emits exactly one event; a rejected one emits none. | Integration |
| BR-29 | Concurrent deposits are all applied: after N deposits the balance has grown by exactly N times the amount. | Integration |

A rule added to the service is added to this table and given a test in the same change.

### 9.3 Rules for Writing Tests

Tests follow the principle in 9.1 first: a test is the readable statement of a business rule. These rules are mandatory.

1. **One rule, one behaviour per test.** A test verifies a single rule under a single condition and can fail for one reason only. Two conditions of the same rule are two tests.
2. **The name states the rule in plain English**, with its id: `BR-07: a withdrawal greater than the balance is rejected and the balance is unchanged`. Someone reading only the test report understands what the service guarantees.
3. **Every test has three visible parts: given, when, then.** They are separated by blank lines. There are no loops, conditionals or calculations in a test body; expected values are written literally (`"150.25"`), not computed.
4. **Tests are independent.** Each test creates the wallets it needs with fresh ids. No test relies on another's data, on execution order, or on shared mutable state. Any test passes when run alone, in any order, or in parallel.
5. **Tests are deterministic.** There are no sleeps and no reliance on wall-clock time. The application takes time from an injected `Clock`, which tests control. Concurrency tests follow the additional rules in section 9.5.
6. **Tests assert what an observer can see:** the HTTP response, the balance, the ledger entries, the outbox rows. They do not verify internal method calls, and the project's own classes are never mocked.
7. **A rejection test also proves nothing changed.** Asserting the status code is not enough; the balance and the ledger are checked too.
8. **Plumbing is hidden behind intention-revealing helpers**, such as `aWalletWithBalance("100.00")` and `deposit(wallet, "50.00")`, so the test body contains only what matters to the rule.
9. **Money is compared by value**, with an explicit expected amount, never with `equals` on differently scaled decimals.

Example of the expected shape:

```java
@Test
@DisplayName("BR-07: a withdrawal greater than the balance is rejected and the balance is unchanged")
void withdrawalGreaterThanBalanceIsRejected() {
    Wallet wallet = aWalletWithBalance("100.00");

    Response response = withdraw(wallet, "100.01");

    assertRejected(response, 422, "INSUFFICIENT_FUNDS");
    assertBalance(wallet, "100.00");
    assertLedgerEntryCount(wallet, 1);
}
```

### 9.4 Test Levels

The risk in this service sits in locks, constraints and transactions, so most rules can only be proven against a real PostgreSQL. Each rule is tested at the lowest level where its test would fail if the rule were broken.

**Unit tests**

- `Money`: BR-03 and BR-04, plus scale normalisation.
- Request fingerprint calculation.
- No mocks of repositories.

**Integration tests**

- Through HTTP with REST Assured, against PostgreSQL in Testcontainers.
- All remaining rules in 9.2, and every error code in section 6.
- BR-19 is also asserted after a randomised run of mixed operations across several wallets. This is the one test allowed a loop, and it lives in its own clearly named class.
- BR-13 is tested exactly at, just before and just after an entry's timestamp, as three tests.
- BR-27 holds the wallet lock from a second connection and asserts the 503.

**End-to-end tests**

Not applicable. There is no frontend, and the integration tests already exercise the full stack through HTTP.

### 9.5 Concurrency Tests on Virtual Threads

Concurrency is the property most likely to be wrong and least likely to be noticed, so it has its own test class and its own rules. These tests cover BR-24, BR-25, BR-26 and BR-29.

**How they run**

- The application under test runs with virtual threads enabled (`spring.threads.virtual.enabled=true`), the same as in production, so the tests exercise the real threading model.
- The client side also uses virtual threads: each simulated caller is one task on `Executors.newVirtualThreadPerTaskExecutor()`, sending a real HTTP request.
- All callers are released at the same instant by a start latch, so the requests genuinely overlap instead of running one after another.
- Each test uses 100 concurrent callers. That is far more than the connection pool holds, so requests really do queue on connections and on the wallet row lock.
- The harness lives in one helper, `runConcurrently(callers, action)`, which returns every response. Test bodies stay free of threads, latches and loops, as rule 3 in section 9.3 requires.

**What each test asserts**

| Rule | Scenario | Assertions |
|---|---|---|
| BR-24 | 100 withdrawals of the full balance from one wallet | Exactly 1 response is 201 and 99 are 422; the balance is zero; the ledger has one withdrawal entry |
| BR-29 | 100 deposits of 1.00 into one wallet | All 100 responses are 201; the balance grew by exactly 100.00; `seq` runs 1 to 100 with no gap or duplicate |
| BR-25 | 50 transfers A→B and 50 transfers B→A at once | Every response is 201; no request failed with a deadlock or timeout; the sum of both balances is unchanged |
| BR-26 | 100 identical requests with the same idempotency key | Every response is 201 with the same operation id; one operation and one ledger entry exist |

**Rules specific to these tests**

- They assert the final state and the full set of responses, never the order in which requests completed.
- Every one of them ends by checking the audit invariant (BR-19) on the wallets involved.
- No response may be 503 `WALLET_BUSY` or 500. A lock timeout or a deadlock under this load is a failure of the design, not an acceptable outcome.
- They use no sleeps and no timing assertions. Each has a generous overall timeout only so that a real deadlock fails the test instead of hanging the build.
- They must pass on every run. A concurrency test that fails occasionally is reporting a real defect and is never retried or disabled.

### 9.6 Coverage Measurement with JaCoCo

JaCoCo measures how much of the whole application the test suite executes. It complements rule coverage in 9.2 and does not replace it: rule coverage says every promise is tested, and JaCoCo shows whether any code is left that no test touches.

- **Scope:** unit and integration tests together, in one merged report. The integration tests run the application in the same JVM, so the code they reach is counted.
- **Report:** HTML and XML generated by the build at `target/site/jacoco`, with the summary figures quoted in the README.
- **Build gate:** the build fails below 90% line coverage and 85% branch coverage.
- **Exclusions:** only the application's `main` class and configuration classes with no logic. Exclusions are listed in the build file with a reason.
- **How uncovered code is handled:** a line no test reaches is either a missing test for a rule, in which case the rule and its test are added, or dead code, in which case it is deleted. Tests are never written only to raise the figure, because a test with no rule behind it breaks rule 1 in section 9.3.

## 10. Assumptions

| # | Assumption | Rationale |
|---|---|---|
| 1 | A user is an opaque `userId` owned by another system and is not validated here. | The assignment defines no user model, and identity belongs outside a wallet microservice. |
| 2 | Callers are trusted upstream services. Authentication and authorisation are out of scope. | The assignment does not mention security, and adding it would displace the core requirements. |
| 3 | A user has one wallet. | "A user's wallet" is singular throughout the assignment. The API addresses wallets by id, so the constraint can be relaxed later without breaking callers. |
| 4 | There is one currency, BRL, and it is not stored. | The assignment mentions no currencies, and RecargaPay operates in Brazil. |
| 5 | Amounts have at most two decimal places and must be positive. Anything else is rejected, never rounded. | Silent rounding makes the caller's books disagree with the ledger. |
| 6 | A balance can never be negative. | The assignment mentions no credit or overdraft. |
| 7 | A deposit is a plain top-up, and a withdrawal a plain removal, with no external payment provider. | The assignment models wallets only. |
| 8 | "A specific point in the past" means the balance after the last operation recorded at or before that instant. | It is the reading an auditor would expect, and it is stable once the instant has passed. |
| 9 | A historical query for an instant in the future is invalid. | Its answer could change, so it is not a historical balance. |
| 10 | A historical query for an instant before the wallet existed returns 404. | The wallet did not exist then, which is different from a wallet with a zero balance. |
| 11 | Timestamps require an explicit offset and are stored in UTC. | It removes ambiguity about the caller's time zone. |
| 12 | Clients generate idempotency keys and keep them stable across retries. | Only the client knows that two requests are the same intent. |

## 11. Trade-offs and Known Risks

| Trade-off or risk | Consequence | Mitigation or evolution |
|---|---|---|
| Writes to one wallet are serialised by its row lock. | A very busy wallet is limited to one write at a time. | Transactions are short; the lock timeout bounds waiting. A hot merchant wallet would need a different model, such as batched settlement. |
| High availability is described, not demonstrated. | The database is a single point of failure in the compose setup. | Managed PostgreSQL with synchronous standby and automatic failover in production. |
| 404 is returned both for an unknown wallet and for an instant before wallet creation. | A caller cannot tell the two apart from the status alone. | Accepted for simplicity. A distinct error code could be added without a breaking change. |
| Amounts are JSON numbers. | Clients that parse numbers as floating point can lose precision. | The server parses into `BigDecimal` and rejects extra decimals. String amounts would remove the risk. |
| Single currency. | No multi-currency wallets or conversion. | Add a currency per wallet and reject cross-currency transfers. |
| Outbox delivery is at-least-once, relayed to a log. | Consumers may see duplicates, and no broker is exercised. | Events carry the operation id; the relay target is one class to replace. |
| Single-entry ledger. | Money entering and leaving the system has no counterparty account. | Evolve to double-entry with system accounts. |
| Idempotency keys are kept indefinitely. | The `operation` table grows with every operation. | It is also the operation record, so it is retained on purpose; partition by time at scale. |
| The lock-timeout mechanism under JPA is unverified. | The planned `SET LOCAL lock_timeout` may need adjusting. | Verified in the first implementation block. |

### Standard Skills Compliance

Not applicable. The project has no `.claude/skills` directory.

### Relevant and Dependent Files

- `specs/Wallet_Service_Assignment.pdf` — the requirements
- `specs/templates/techspec-template.md` — the template this document covers
- `specs/prd.md` — the product requirements
- `specs/tasks.md` and `specs/task-*.md` — the implementation tasks

## 12. Development Sequencing

### Build Order

| # | Block | Why in this position | Time |
|---|---|---|---|
| 1 | Project scaffold, schema and migrations, `Money`, entities | Everything else depends on the model; the lock-timeout question is settled here | 1.5h |
| 2 | Create wallet, current balance, deposit and withdrawal with locking and ledger | The core write path, with the first concurrency test | 2h |
| 3 | Transfers and idempotency | Builds on the write path; adds ordered locking and the replay flow | 1.5h |
| 4 | Historical balance and ledger endpoint | Read-only, on top of existing data | 1h |
| 5 | Outbox, metrics and logs | Additive; touches the write path in one place | 1h |
| 6 | Docker, README and design write-up | Required deliverables | 1h |

Each block includes the tests for the business rules it implements; a block is not finished until its rules in section 9.2 are covered. The table totals 8 hours with no buffer. With full rule coverage, the concurrency tests and the coverage gate, the per-task estimates in `tasks.md` total about 10 hours.

If time runs short, the outbox publisher and the ledger endpoint are cut first and recorded as trade-offs. Tests and readability are never what is cut: a feature is dropped whole, with its rules, before it is shipped untested. The actual time spent is reported in the README, as the assignment asks.

### Technical Dependencies

- Docker, for Testcontainers and docker-compose.
- JDK 25.
- No external services.
