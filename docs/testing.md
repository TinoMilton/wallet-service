# Testing

[← Back to the README](../README.md)

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

**Measured totals from the final run** (after the last code change): 135 tests, all passing; 96.45% line and 87.50% branch coverage, above the 90% / 85% build gate; `ConcurrencyTest` passed 5 consecutive runs.

## Continuous integration

Every push to `main` runs `./mvnw verify` on GitHub Actions ([`.github/workflows/ci.yml`](../.github/workflows/ci.yml)): the whole suite against Testcontainers PostgreSQL, the Spotless format check and the coverage gate. The surefire, failsafe and JaCoCo reports are kept as a build artifact for 14 days, also when the build fails. The workflow can be started by hand from the Actions tab.
