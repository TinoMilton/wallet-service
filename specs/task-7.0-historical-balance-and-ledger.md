# Task 7.0: Historical Balance and Ledger Endpoint

<critical>Read the prd.md and techspec.md files in this folder. If you do not read these files, your task will be invalidated.</critical>

## Overview

Add the two read features built on the ledger: the balance of a wallet at a past instant, and the paged list of a wallet's ledger entries for audit.

**Execution:** stage 5, in parallel with task 5.0. Starts when 4.0 is merged. Follow the file ownership and parallel-agent rules in `tasks.md`: do not edit `LedgerService`, `TransferController` or `ConcurrencyTest`.

<skills>
### Standard Skills Compliance

None. The project has no `.claude/skills` directory.
</skills>

<requirements>
- `GET /api/v1/wallets/{walletId}/balance?at={ISO-8601}` returns the `balance_after` of the last entry at or before `at`, ordered by `seq`.
- `at` is inclusive.
- An existing wallet with no entries up to `at` returns zero.
- `at` before the wallet's creation returns 404 `WALLET_NOT_FOUND`.
- `at` in the future, malformed, or without an offset returns 400 `INVALID_TIMESTAMP`. "Future" is judged against the `Clock`.
- The query uses the `(wallet_id, created_at)` index and takes no lock.
- `GET /api/v1/wallets/{walletId}/entries` returns a page of entries, newest first; an unknown wallet returns 404.
- Stored timestamps and test instants use the same precision, so boundary tests are exact.
</requirements>

## Subtasks

- [ ] 7.1 Implement the historical balance query in `LedgerEntryRepository`
- [ ] 7.2 Implement `WalletService.balanceAt` with its timestamp rules
- [ ] 7.3 Add the `at` parameter to the balance endpoint
- [ ] 7.4 Write the tests for BR-13, BR-14, BR-15 and BR-16
- [ ] 7.5 Implement `WalletService.entries` and the entries endpoint
- [ ] 7.6 Write the tests for the entries endpoint

If time is short, subtasks 7.5 and 7.6 are cut and recorded as a trade-off.

## Implementation Details

- Historical query: `techspec.md` section 5, "Queries".
- Endpoint, parameters and error codes: section 6.
- Meaning of a past instant and edge cases: section 10, assumptions 8 to 11.
- Controlling time in tests: section 9.3, rule 5.

## Success Criteria

- Each business rule listed above has a passing dedicated test.
- BR-13 is proven by three separate tests: exactly at an entry's timestamp, just before it and just after it.
- No test uses a sleep; all instants come from the test clock.
- The whole suite and the JaCoCo gate pass.

## Task Tests

- [ ] Unit tests: none
- [ ] Integration tests:
  - BR-13 balance exactly at an entry's timestamp includes that entry
  - BR-13 balance just before an entry's timestamp excludes it
  - BR-13 balance just after the last entry equals the current balance
  - BR-14 existing wallet with no operations up to the instant returns zero
  - BR-15 instant before wallet creation is rejected with 404
  - BR-16 future instant is rejected; malformed instant is rejected; instant without offset is rejected
  - Entries endpoint returns entries newest first; page size is respected; unknown wallet is 404
- [ ] E2E tests: not applicable

<critical>ALWAYS CREATE AND RUN THE TASK'S TESTS BEFORE CONSIDERING IT FINISHED</critical>

## Relevant Files

- `src/main/java/com/recargapay/wallet/repository/LedgerEntryRepository.java`
- `src/main/java/com/recargapay/wallet/service/WalletService.java`
- `src/main/java/com/recargapay/wallet/api/WalletController.java`
- `src/main/java/com/recargapay/wallet/api/dto/`
- `src/test/java/com/recargapay/wallet/support/MutableClock.java`
- `src/test/java/com/recargapay/wallet/wallet/HistoricalBalanceTest.java`
- `src/test/java/com/recargapay/wallet/ledger/LedgerEntriesTest.java`
