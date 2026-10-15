# Assumptions and trade-offs

[← Back to the README](../README.md)

## Assumptions

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

## Trade-offs and compromises

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
