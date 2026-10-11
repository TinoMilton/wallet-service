# Task 2.0: Database Schema and Domain Model

<critical>Read the prd.md and techspec.md files in this folder. If you do not read these files, your task will be invalidated.</critical>

## Overview

Create the four tables, the trigger that makes the ledger unchangeable, the JPA entities and repositories, and the domain value types used in every later task.

<skills>
### Standard Skills Compliance

None. The project has no `.claude/skills` directory.
</skills>

<requirements>
- Flyway migrations create `wallet`, `operation`, `ledger_entry` and `outbox_event` exactly as in the tech spec, with all constraints and indexes.
- A database trigger rejects `UPDATE` and `DELETE` on `ledger_entry`.
- `Money` holds a `BigDecimal` at scale 2. It rejects zero, negative values and more than two decimal places, and never rounds.
- `WalletId` and `IdempotencyKey` are value types; signatures use them instead of `UUID` and `String`.
- `Operation` and `LedgerEntry` entities have no setters and are marked `@Immutable`.
- Jackson reads JSON numbers into `BigDecimal` and writes plain notation with two decimals.
- The schema validates against the entities (`ddl-auto=validate`).
</requirements>

## Subtasks

- [ ] 2.1 Write the migration for the four tables, constraints and indexes
- [ ] 2.2 Write the migration for the ledger immutability trigger
- [ ] 2.3 Implement `Money` with its validation
- [ ] 2.4 Implement `WalletId` and `IdempotencyKey`
- [ ] 2.5 Implement the entities `Wallet`, `Operation`, `LedgerEntry` and `OutboxEvent`
- [ ] 2.6 Implement the four repositories
- [ ] 2.7 Configure Jackson for amounts
- [ ] 2.8 Write the tests for BR-03, BR-04 and BR-18

## Implementation Details

- Tables, constraints and invariants: `techspec.md` section 5.
- Money and amount rules: section 10, assumptions 4 to 6.
- Entity immutability: section 7, "Traceability".
- Domain types and naming: section 9.1.

## Success Criteria

- The application starts with `ddl-auto=validate` against a migrated database.
- BR-03, BR-04 and BR-18 each have a passing dedicated test.
- No public signature in the domain uses a raw `BigDecimal` for money.
- The whole suite and the JaCoCo gate pass.

## Task Tests

- [ ] Unit tests: `Money` — BR-03 (zero, negative), BR-04 (three decimals rejected, not rounded), scale normalisation, value comparison
- [ ] Integration tests: BR-18 — a direct SQL `UPDATE` and a direct SQL `DELETE` on `ledger_entry` are each rejected
- [ ] E2E tests: not applicable

<critical>ALWAYS CREATE AND RUN THE TASK'S TESTS BEFORE CONSIDERING IT FINISHED</critical>

## Relevant Files

- `src/main/resources/db/migration/V1__create_schema.sql`
- `src/main/resources/db/migration/V2__ledger_entry_immutable.sql`
- `src/main/java/com/recargapay/wallet/domain/Money.java`
- `src/main/java/com/recargapay/wallet/domain/WalletId.java`
- `src/main/java/com/recargapay/wallet/domain/IdempotencyKey.java`
- `src/main/java/com/recargapay/wallet/domain/Wallet.java`
- `src/main/java/com/recargapay/wallet/domain/Operation.java`
- `src/main/java/com/recargapay/wallet/domain/LedgerEntry.java`
- `src/main/java/com/recargapay/wallet/domain/OutboxEvent.java`
- `src/main/java/com/recargapay/wallet/repository/`
- `src/main/java/com/recargapay/wallet/config/JacksonConfig.java`
- `src/test/java/com/recargapay/wallet/domain/MoneyTest.java`
- `src/test/java/com/recargapay/wallet/ledger/LedgerImmutabilityTest.java`
