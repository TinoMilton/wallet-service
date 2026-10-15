# Design

[← Back to the README](../README.md)

## How the design meets the requirements

**Create wallet.** `WalletService.create` inserts the wallet with a zero balance; a unique constraint on `user_id` makes a second wallet for the same user a 409, detected by constraint name in `WalletService`.

**Retrieve balance.** The current balance is the cached `wallet.balance` column, read without a lock in `WalletService.currentBalance`. The cache is kept exact by the write flow (see below).

**Retrieve historical balance.** Every ledger entry stores `balance_after`, so a past balance is one indexed read: the last entry at or before the instant (`LedgerEntryRepository.lastEntryAtOrBefore`, using the `(wallet_id, created_at)` index). The instant is truncated to microseconds before comparing, because PostgreSQL stores microseconds and a finer cutoff could round up to a later entry (`WalletService.atMicrosecondPrecision`).

**Deposit and withdraw.** Each command is one database transaction in `LedgerService`: it takes a pessimistic lock on the wallet row (`WalletRepository.lockById`) with a 2-second `lock_timeout` (`SET LOCAL`, because PostgreSQL ignores the positive JPA lock-timeout hint), checks funds, appends one immutable ledger entry with the balance after it, and updates the cached balance in the same transaction. Rejections roll back, so nothing changes.

**Transfer.** Same flow, plus: both wallets are locked in ascending id order so two opposing transfers cannot deadlock (`LedgerService.lockWalletPair`), and the debit and credit are two ledger entries under one operation in one transaction. The balance check happens under the lock, so a concurrent withdrawal cannot make both sides still "succeed".

**Safe retries.** Every money-moving request carries an `Idempotency-Key`. The operation row with its unique key is inserted and flushed before any wallet lock is taken (`LedgerService.saveOperation`), so a concurrent duplicate waits on the unique index while holding no lock. A duplicate then replays in a fresh read-only transaction (`LedgerService.replay`) and returns the original result; the same key with a different request is rejected by comparing the stored `RequestFingerprint`.

**Mission-critical (availability).** The application is stateless: no in-process state, no local locks, so several instances can run side by side. Readiness includes the database (`application.yml`), shutdown is graceful, the connection pool is bounded, and a request that waits too long on a wallet lock fails with a retryable 503 rather than hanging. What is not demonstrated here is described in [Trade-offs](assumptions-and-trade-offs.md#trade-offs-and-compromises).

**Full traceability (audit).** The append-only ledger is the audit trail: entries are immutable, enforced by a database trigger (`V2__ledger_entry_immutable.sql`) and by entities with no setters marked `@Immutable`. Each entry links to the operation that caused it, and `GET /entries` exposes the chain. Every operation is announced through a transactional outbox written in the same transaction (`OutboxPublisher`), every operation is logged once with `requestId`, `walletId` and `operationId` (`OperationMetrics`, `RequestIdFilter`), and operation outcomes are counted in Prometheus metrics.

## Design decisions and rejected alternatives

These are the decisions from `specs/techspec.md` section 2, with the reasons. The full argument for each is in the tech spec.

- **Java 25 LTS, not Java 27.** Java 27 is a non-LTS release with a short support window, outside the range Spring Boot 4.1 documents. Nothing in a wallet service needs it.
- **Synchronous commands, not async (202 + poll).** Deposits and withdrawals must confirm their outcome to the caller. Asynchrony would bring eventual consistency into the most consistency-critical path. Asynchrony is kept for event publication only, via the outbox.
- **Ledger as the source of truth, not CQRS or event sourcing.** The historical-balance and audit requirements are properties of an immutable chain of entries. A separate read model would add projection lag and double the test surface for no new guarantee.
- **Ledger first, not a balance column plus a side audit log.** Nothing would tie the balance to the log, so an auditor could observe a balance but not prove it.
- **Pessimistic row locks, not optimistic `@Version`.** A version conflict fails a legitimate request; under contention on a busy wallet, incoming deposits would be rejected for no business reason. With row locks, concurrent requests queue for milliseconds instead.
- **Single-entry ledger, not full double-entry bookkeeping (deferred, not rejected).** Double entry needs system and external accounts, and the assignment has no counterparty for deposits and withdrawals. It is the natural production evolution.
- **PostgreSQL, not MongoDB.** Transfers need multi-row ACID transactions and row-level locks.
- **Transactional outbox, not Kafka (for this build).** A broker and consumers are excess infrastructure for one service in this time budget. The outbox is written in the same transaction as the money movement, so there is no dual write; the relay target is one class to replace.
- **One app and one PostgreSQL in compose, not a multi-instance HA setup.** The time went to the outbox instead. The application is stateless, so scaling it is a deployment change; database failover is described, not built ([Trade-offs](assumptions-and-trade-offs.md#trade-offs-and-compromises)).
