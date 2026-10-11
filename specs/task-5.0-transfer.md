# Task 5.0: Transfer Between Wallets

<critical>Read the prd.md and techspec.md files in this folder. If you do not read these files, your task will be invalidated.</critical>

## Overview

Add transfers: one operation that debits the sender and credits the receiver in a single transaction, with both wallets locked in a fixed order so opposing transfers cannot deadlock.

**Execution:** stage 5, in parallel with task 7.0. Starts when 4.0 is merged. Follow the file ownership and parallel-agent rules in `tasks.md`: do not edit `WalletController`, `WalletService` or `LedgerEntryRepository`.

<skills>
### Standard Skills Compliance

None. The project has no `.claude/skills` directory.
</skills>

<requirements>
- `POST /api/v1/transfers` returns 201 with the operation id and the sender's resulting balance.
- Both wallets are locked in ascending `wallet_id` order, whichever is the sender.
- One `operation` row and two `ledger_entry` rows are written: negative for the sender, positive for the receiver.
- A transfer greater than the sender's balance returns 422 `INSUFFICIENT_FUNDS` and changes neither wallet.
- A transfer from a wallet to itself returns 400 `SAME_WALLET_TRANSFER`.
- An unknown wallet on either side returns 404 `WALLET_NOT_FOUND`.
- The `Idempotency-Key` header is required, as for deposits.
- The transfer reuses the entry-appending step from task 4.0; it does not duplicate it.
</requirements>

## Subtasks

- [x] 5.1 Implement ordered locking of two wallets
- [x] 5.2 Implement `LedgerService.transfer`
- [x] 5.3 Implement `TransferController`
- [x] 5.4 Add the test helper `transfer(from, to, amount)`
- [x] 5.5 Write the tests for BR-08, BR-09, BR-10, BR-11 (transfer) and BR-17 (transfer)
- [x] 5.6 Write the concurrency test for BR-25
- [x] 5.7 Write the randomised mixed-operations test for BR-19

## Implementation Details

- Transfer in the write flow: `techspec.md` section 4.
- Two entries under one operation: section 5, invariant 3.
- Ordered locking: section 7, "Concurrency".
- Endpoint and response shape: section 6.
- BR-25 scenario and the randomised BR-19 test: sections 9.4 and 9.5.

## Success Criteria

- Each business rule listed above has a passing dedicated test.
- The BR-25 test (50 transfers each way) sees every response as 201, with no deadlock and no timeout, on ten consecutive runs.
- The randomised test leaves the audit invariant true for every wallet.
- The whole suite and the JaCoCo gate pass.

## Task Tests

- [ ] Unit tests: none
- [ ] Integration tests:
  - BR-08 transfer debits the sender and credits the receiver by the same amount
  - BR-09 transfer above the sender's balance is rejected and neither wallet changes
  - BR-10 transfer to the same wallet is rejected
  - BR-11 unknown sender is rejected; unknown receiver is rejected and the sender is unchanged
  - BR-17 a transfer records two entries under one operation
  - BR-25 opposing concurrent transfers complete without deadlock and conserve the total
  - BR-19 audit invariant holds after a randomised run of mixed operations
- [ ] E2E tests: not applicable

<critical>ALWAYS CREATE AND RUN THE TASK'S TESTS BEFORE CONSIDERING IT FINISHED</critical>

## Relevant Files

- `src/main/java/com/recargapay/wallet/service/LedgerService.java`
- `src/main/java/com/recargapay/wallet/api/TransferController.java`
- `src/main/java/com/recargapay/wallet/api/dto/`
- `src/test/java/com/recargapay/wallet/support/WalletTestActions.java`
- `src/test/java/com/recargapay/wallet/ledger/TransferTest.java`
- `src/test/java/com/recargapay/wallet/concurrency/ConcurrencyTest.java`
- `src/test/java/com/recargapay/wallet/ledger/AuditInvariantRandomisedTest.java`
