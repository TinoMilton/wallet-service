# Task 3.0: Create Wallet and Current Balance

<critical>Read the prd.md and techspec.md files in this folder. If you do not read these files, your task will be invalidated.</critical>

## Overview

Deliver the first two endpoints, create wallet and current balance, together with the error format used by the whole API and the test helpers later tasks build on.

<skills>
### Standard Skills Compliance

None. The project has no `.claude/skills` directory.
</skills>

<requirements>
- `POST /api/v1/wallets` creates a wallet for a `userId` and returns 201 with a zero balance.
- A second wallet for the same user returns 409 `WALLET_ALREADY_EXISTS`. The unique constraint is the guard, not a prior lookup.
- `GET /api/v1/wallets/{walletId}/balance` returns the cached balance with `asOf`.
- An unknown wallet returns 404 `WALLET_NOT_FOUND`.
- All errors are RFC 7807 problem details with a stable `code` field, produced in one place.
- A missing or blank `userId` returns 400.
</requirements>

## Subtasks

- [x] 3.1 Implement `GlobalExceptionHandler` and the problem-detail format with `code`
- [x] 3.2 Implement `WalletService.create` and `WalletService.currentBalance`
- [x] 3.3 Implement `WalletController` for the two endpoints
- [x] 3.4 Add the test helpers `aWallet()`, `assertBalance(...)` and `assertRejected(...)`
- [x] 3.5 Write the tests for BR-01, BR-02 and BR-11 (balance)

## Implementation Details

- Endpoints, shapes and error codes: `techspec.md` section 6.
- Components: section 4.
- User and wallet assumptions: section 10, assumptions 1 to 3.
- Test helpers and shape: section 9.3, rules 6 and 8.

## Success Criteria

- BR-01, BR-02 and BR-11 (for the balance endpoint) each have a passing dedicated test.
- Every error response in this task has the content type `application/problem+json` and a `code`.
- The whole suite and the JaCoCo gate pass.

## Task Tests

- [ ] Unit tests: none
- [x] Integration tests: BR-01 new wallet has zero balance; BR-02 second wallet for the user is rejected with 409; BR-11 balance of an unknown wallet is 404; blank `userId` is 400
- [ ] E2E tests: not applicable

<critical>ALWAYS CREATE AND RUN THE TASK'S TESTS BEFORE CONSIDERING IT FINISHED</critical>

## Relevant Files

- `src/main/java/com/recargapay/wallet/api/WalletController.java`
- `src/main/java/com/recargapay/wallet/api/GlobalExceptionHandler.java`
- `src/main/java/com/recargapay/wallet/api/dto/`
- `src/main/java/com/recargapay/wallet/service/WalletService.java`
- `src/main/java/com/recargapay/wallet/domain/exception/`
- `src/test/java/com/recargapay/wallet/support/WalletTestActions.java`
- `src/test/java/com/recargapay/wallet/wallet/CreateWalletTest.java`
- `src/test/java/com/recargapay/wallet/wallet/CurrentBalanceTest.java`
