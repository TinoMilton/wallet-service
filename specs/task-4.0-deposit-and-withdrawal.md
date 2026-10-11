# Task 4.0: Deposit and Withdrawal

<critical>Read the prd.md and techspec.md files in this folder. If you do not read these files, your task will be invalidated.</critical>

## Overview

Build the core write path: lock the wallet, check funds, append a ledger entry, update the cached balance, all in one transaction. This task also builds the concurrency test harness on virtual threads and proves the locking design.

<skills>
### Standard Skills Compliance

None. The project has no `.claude/skills` directory.
</skills>

<requirements>
- `POST /api/v1/wallets/{walletId}/deposits` and `/withdrawals` return 201 with the operation id and resulting balance.
- The wallet row is locked with `SELECT ... FOR UPDATE`, and that locking query is the first read of the wallet in the transaction.
- The transaction sets a 2-second lock timeout. A timeout returns 503 `WALLET_BUSY` and moves no money.
- Verify how the JPA lock-timeout hint behaves on PostgreSQL and record the finding; use `SET LOCAL lock_timeout` unless the hint is proven to work.
- Each operation inserts one `operation` row and one `ledger_entry` with the next `seq`, the signed amount and `balance_after`. `seq` and `created_at` are assigned under the lock, with time taken from the `Clock`.
- `wallet.balance` is updated in the same transaction.
- A withdrawal greater than the balance returns 422 `INSUFFICIENT_FUNDS` and changes nothing.
- The `Idempotency-Key` header is required (400 `MISSING_IDEMPOTENCY_KEY`) and stored with the request fingerprint. Replay behaviour is task 6.0.
- `LedgerService` reads as the steps of the write flow, one named method per step.
</requirements>

## Subtasks

- [x] 4.1 Implement the locking wallet query and the lock-timeout setting; record the JPA hint finding
- [x] 4.2 Implement `LedgerService.deposit` and `LedgerService.withdraw`
- [x] 4.3 Implement the request fingerprint
- [x] 4.4 Implement the two endpoints, including the required idempotency header
- [x] 4.5 Map insufficient funds, lock timeout and missing key to their problem details
- [x] 4.6 Add the test helpers `aWalletWithBalance(...)`, `deposit(...)`, `withdraw(...)`, ledger assertions and `assertAuditInvariant(...)`
- [x] 4.7 Write the tests for BR-05, BR-06, BR-07, BR-11, BR-12, BR-17, BR-19, BR-20 and BR-27
- [x] 4.8 Implement the `runConcurrently(callers, action)` harness on virtual threads with a start latch
- [x] 4.9 Write the concurrency tests for BR-24 and BR-29

## Implementation Details

- Write flow: `techspec.md` section 4.
- Ledger model and invariants: section 5.
- Locking, timeout and idempotency storage: section 7.
- Concurrency test design and assertions: section 9.5.
- Readability of the service method: section 9.1.

## Success Criteria

- Each business rule listed above has a passing dedicated test.
- The BR-24 and BR-29 tests use 100 concurrent callers, see no 503 or 500, and pass on ten consecutive runs.
- The audit invariant holds at the end of every concurrency test.
- The whole suite and the JaCoCo gate pass.

## Task Tests

- [x] Unit tests: request fingerprint is equal for equal requests and different for different ones
- [x] Integration tests:
  - BR-05 deposit increases the balance by exactly the amount
  - BR-06 withdrawal decreases the balance by exactly the amount
  - BR-07 withdrawal above the balance is rejected and nothing changes
  - BR-11 deposit and withdrawal on an unknown wallet are rejected
  - BR-12 current balance equals the balance after the latest operation
  - BR-17 each operation records one entry with the right amount, `seq` and `balance_after`
  - BR-19 audit invariant holds after a sequence of operations
  - BR-20 request without an idempotency key is rejected
  - BR-27 request that cannot obtain the lock is rejected with 503 and moves no money
  - BR-24 of 100 concurrent full-balance withdrawals exactly one succeeds
  - BR-29 100 concurrent deposits are all applied
- [ ] E2E tests: not applicable

<critical>ALWAYS CREATE AND RUN THE TASK'S TESTS BEFORE CONSIDERING IT FINISHED</critical>

## Relevant Files

- `src/main/java/com/recargapay/wallet/service/LedgerService.java`
- `src/main/java/com/recargapay/wallet/service/RequestFingerprint.java`
- `src/main/java/com/recargapay/wallet/repository/WalletRepository.java`
- `src/main/java/com/recargapay/wallet/api/WalletController.java`
- `src/main/java/com/recargapay/wallet/api/GlobalExceptionHandler.java`
- `src/test/java/com/recargapay/wallet/support/ConcurrentCallers.java`
- `src/test/java/com/recargapay/wallet/support/WalletTestActions.java`
- `src/test/java/com/recargapay/wallet/ledger/DepositTest.java`
- `src/test/java/com/recargapay/wallet/ledger/WithdrawalTest.java`
- `src/test/java/com/recargapay/wallet/ledger/WalletBusyTest.java`
- `src/test/java/com/recargapay/wallet/concurrency/ConcurrencyTest.java`
