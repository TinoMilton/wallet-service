# AGENTS.md

Single source of truth for every coding agent in this repo (Claude Code, Codex, Droid). `CLAUDE.md` only imports this file. Keep it short: every line here is paid for by every agent session.

## Project

**Wallet Service** — RecargaPay take-home. A Java microservice that creates wallets and moves money: deposit, withdraw, transfer, current balance and balance at a past instant. Mission-critical, and every operation must be traceable.

Stack: Java 25 · Spring Boot 4.1.x (Web MVC, Validation, Data JPA, Actuator) · virtual threads · PostgreSQL · Flyway · Maven · JUnit 5 + Testcontainers + REST Assured · JaCoCo.

## Layout

```
src/main/java/com/recargapay/wallet/
  api/            controllers, DTOs, GlobalExceptionHandler
  service/        WalletService, LedgerService, IdempotentCommandExecutor
  domain/         entities, Money, WalletId, IdempotencyKey, exceptions
  repository/     Spring Data JPA repositories
  outbox/         outbox publisher and relay
  observability/  metrics, request id
  config/
src/main/resources/db/migration/   Flyway migrations
src/test/java/com/recargapay/wallet/support/   shared test helpers
specs/            prd.md, techspec.md, tasks.md, task-N.0-*.md
docs/orchestration.md   coordinator rules
```

Data flow: `controller → service → repository → PostgreSQL`.

## Commands

```bash
./mvnw verify                      # everything: unit + integration tests, formatter check, JaCoCo gate
./mvnw test -Dtest=<ClassName>     # one test class
docker compose up -d               # local PostgreSQL
```

Integration tests run against a real PostgreSQL through Testcontainers and need Docker. Never mock the database.

## Conventions

- **The code is written for humans to read, not for machines.** Follow `specs/techspec.md` section 9.1: domain names, one-purpose methods, domain types instead of primitives, no cleverness.
- Tests follow `specs/techspec.md` section 9.3: one rule and one behaviour per test, rule id in the display name, given/when/then, independent, deterministic, no sleeps.
- Business rules are the BR-01 to BR-29 catalogue in `specs/techspec.md` section 9.2. A new rule gets a row there and a test in the same change.
- Money is always `Money`, never a bare `BigDecimal`, `double` or `float`. Never round.
- Time comes only from the injected `Clock`.
- Ledger entries are immutable: never update or delete one.
- Errors are RFC 7807 problem details with a stable `code`, produced in `GlobalExceptionHandler`.
- Tests ship with the change. The build gate is 90% line and 85% branch coverage.
- Commit after a task's tests pass and before starting the next one.

## Working Here as an Agent

- **Read only what the task names.** Do not explore the repo, and do not read other tasks' files unless your task points to them.
- **Stay in scope.** Edit only the files your task assigns. Need something outside it? Ask, don't edit. In a parallel stage, the file ownership table in `specs/tasks.md` is binding.
- **Verify before reporting.** Run the task's check command and report the real result, including failures.
- **No report documents.** Do not create evidence, report or review `.md` files. The proof of your work is the code, the tests and your `worker_done` summary. Write to a doc only when the task names that doc.
- **Do not tick tasks in `specs/tasks.md`.** Tick subtasks only in your own task file; the coordinator ticks the task.
- **Do not improvise on the spec.** If the tech spec is wrong or incomplete, or you need a new migration or dependency your task does not name, stop and describe it.
- **Context budget.** Before a session passes 40% of its context, write resumable state to the task's file, `/compact`, and continue the same task.
- **Dispatched by Orca?** The injected preamble is authoritative. Finish with exactly one `worker_done` (`--outcome succeeded|failed`) whose body is at most about 10 lines: what changed, files modified, decisions, what is left. Heartbeats only on work longer than 10 minutes, one line each. Then idle.
- **Dispatched headless (Droid or Codex)?** End by printing the same summary of at most about 10 lines.
- **Coordinating a multi-agent run?** Read `docs/orchestration.md` first. Workers do not need it.
