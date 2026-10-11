# Task 6.0: Idempotent Replay

<critical>Read the prd.md and techspec.md files in this folder. If you do not read these files, your task will be invalidated.</critical>

## Overview

Make retries safe. Tasks 4.0 and 5.0 already require and store the idempotency key; this task defines what happens when a key arrives a second time, for all three money-moving operations.

**Execution:** stage 6, alone. Starts when 5.0 and 7.0 are both merged, because it edits both controllers and `LedgerService`.

<skills>
### Standard Skills Compliance

None. The project has no `.claude/skills` directory.
</skills>

<requirements>
- `IdempotentCommandExecutor` wraps deposit, withdrawal and transfer.
- The unique constraint on `operation.idempotency_key` is the guard. There is no lookup before the insert.
- On a duplicate key, the failed transaction rolls back completely, and the original operation is loaded in a new transaction, outside the failed one.
- Same key and same fingerprint: return the original result with 201 and the same body.
- Same key and different fingerprint: return 422 `IDEMPOTENCY_KEY_REUSED`.
- A rejected operation is not stored, so a retry with the same key is evaluated again.
- A duplicate key never produces a 500.
</requirements>

## Subtasks

- [x] 6.1 Implement `IdempotentCommandExecutor` with the replay path in a new transaction
- [x] 6.2 Rebuild the original response from the stored operation and its entries
- [x] 6.3 Route the three endpoints through the executor
- [x] 6.4 Map key reuse with a different request to its problem detail
- [x] 6.5 Write the tests for BR-21, BR-22 and BR-23
- [x] 6.6 Write the concurrency test for BR-26

## Implementation Details

- Replay flow and transaction boundary: `techspec.md` sections 4 and 7, "Idempotency".
- Why the replay runs outside the failed transaction: section 7, "JPA Rules", and section 2.
- BR-26 scenario: section 9.5.

## Success Criteria

- Each business rule listed above has a passing dedicated test, for deposit, withdrawal and transfer where the rule applies.
- The BR-26 test (100 identical requests) sees every response as 201 with the same operation id, and one operation in the database, on ten consecutive runs.
- The whole suite and the JaCoCo gate pass.

## Task Tests

- [ ] Unit tests: none
- [ ] Integration tests:
  - BR-21 repeating a deposit with the same key returns the original result and the balance moves once
  - BR-21 the same for a withdrawal and for a transfer
  - BR-22 the same key with a different amount is rejected with 422 and nothing changes
  - BR-23 a withdrawal rejected for insufficient funds succeeds on retry with the same key once the wallet is funded
  - BR-26 the same key sent by 100 concurrent callers produces one operation
- [ ] E2E tests: not applicable

<critical>ALWAYS CREATE AND RUN THE TASK'S TESTS BEFORE CONSIDERING IT FINISHED</critical>

## Relevant Files

- `src/main/java/com/recargapay/wallet/service/IdempotentCommandExecutor.java`
- `src/main/java/com/recargapay/wallet/service/LedgerService.java`
- `src/main/java/com/recargapay/wallet/repository/OperationRepository.java`
- `src/main/java/com/recargapay/wallet/api/WalletController.java`
- `src/main/java/com/recargapay/wallet/api/TransferController.java`
- `src/test/java/com/recargapay/wallet/idempotency/IdempotentReplayTest.java`
- `src/test/java/com/recargapay/wallet/concurrency/ConcurrencyTest.java`
