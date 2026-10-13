# Wallet Service Implementation Task Summary

Read `prd.md` and `techspec.md` in this folder before starting any task.

## Tasks

- [x] 1.0 Project scaffold and build tooling — [task-1.0-project-scaffold.md](task-1.0-project-scaffold.md)
- [x] 2.0 Database schema and domain model — [task-2.0-schema-and-domain-model.md](task-2.0-schema-and-domain-model.md)
- [x] 3.0 Create wallet and current balance — [task-3.0-create-wallet-and-balance.md](task-3.0-create-wallet-and-balance.md)
- [x] 4.0 Deposit and withdrawal — [task-4.0-deposit-and-withdrawal.md](task-4.0-deposit-and-withdrawal.md)
- [x] 5.0 Transfer between wallets — [task-5.0-transfer.md](task-5.0-transfer.md)
- [x] 6.0 Idempotent replay — [task-6.0-idempotent-replay.md](task-6.0-idempotent-replay.md)
- [x] 7.0 Historical balance and ledger endpoint — [task-7.0-historical-balance-and-ledger.md](task-7.0-historical-balance-and-ledger.md)
- [x] 8.0 Transactional outbox and publisher — [task-8.0-outbox.md](task-8.0-outbox.md)
- [x] 9.0 Observability and operational readiness — [task-9.0-observability.md](task-9.0-observability.md)
- [x] 10.0 Packaging and documentation — [task-10.0-packaging-and-docs.md](task-10.0-packaging-and-docs.md)

## Execution Plan for Agent Orchestration

Tasks run in eight stages. Tasks in the same stage run in parallel, each with its own agent. A stage starts only when every task of the previous stage is merged and the full build passes on the main branch.

```
Stage 1   1.0
Stage 2   2.0
Stage 3   3.0
Stage 4   4.0
Stage 5   5.0  ║  7.0
Stage 6   6.0
Stage 7   8.0  ║  9.0
Stage 8   10.0
```

| Stage | Tasks | Mode | Starts when | Wall-clock estimate |
|---|---|---|---|---|
| 1 | 1.0 | Single agent | — | 0.75h |
| 2 | 2.0 | Single agent | 1.0 merged | 1h |
| 3 | 3.0 | Single agent | 2.0 merged | 0.75h |
| 4 | 4.0 | Single agent | 3.0 merged | 2h |
| 5 | 5.0 and 7.0 | **Parallel, 2 agents** | 4.0 merged | 1.25h |
| 6 | 6.0 | Single agent | 5.0 and 7.0 both merged | 1h |
| 7 | 8.0 and 9.0 | **Parallel, 2 agents** | 6.0 merged | 0.75h |
| 8 | 10.0 | Single agent | everything merged | 1h |

Estimated work is about 10 hours in total; with the two parallel stages the elapsed time is about 8.5 hours. These are estimates for planning, not measurements.

### Why the First Four Stages Are Sequential

Each of tasks 1.0 to 4.0 creates what the next one is built from: the build, then the schema and domain types, then the error format and test helpers, then the write path and the concurrency harness. Running them in parallel would have agents inventing the same foundations in different ways.

One optional split inside stage 4: subtask 4.8, the `runConcurrently` harness, touches only `src/test/java/.../support/ConcurrentCallers.java` and can be given to a second agent while the first does 4.1 to 4.7.

### Why These Pairs Are Safe in Parallel

**Stage 5: 5.0 and 7.0.** Transfer is a write feature; historical balance and the ledger endpoint are read features. They share no production class.

**Stage 7: 8.0 and 9.0.** The outbox changes the write flow and its own package; observability adds cross-cutting classes and configuration. Task 8.0 owns the outbox metrics so that 9.0 never touches outbox code.

### Why 6.0 Runs Alone

Idempotent replay reroutes all three money endpoints through the executor, so it edits both controllers and `LedgerService`. Those are the files 5.0 and 7.0 change, and the ones 8.0 and 9.0 change next. Running it between the two parallel stages keeps every parallel pair free of overlapping edits.

### File Ownership in Parallel Stages

An agent edits only the files it owns. If it needs a change in a file owned by the other agent in its stage, it stops and reports to the orchestrator instead of editing.

| Stage | Task | Owns (may create and edit) | Must not edit |
|---|---|---|---|
| 5 | 5.0 | `LedgerService`, `TransferController`, transfer DTOs, `TransferTest`, `ConcurrencyTest`, `AuditInvariantRandomisedTest` | `WalletController`, `WalletService`, `LedgerEntryRepository` |
| 5 | 7.0 | `WalletService`, `WalletController`, `LedgerEntryRepository`, balance and entry DTOs, `MutableClock`, `HistoricalBalanceTest`, `LedgerEntriesTest` | `LedgerService`, `TransferController`, `ConcurrencyTest` |
| 7 | 8.0 | `LedgerService`, everything under `outbox/`, `OutboxEventRepository`, outbox tests | Controllers, `observability/`, `logback-spring.xml` |
| 7 | 9.0 | Everything under `observability/`, `OpenApiConfig`, controllers (documentation annotations only), `logback-spring.xml`, observability tests | `LedgerService`, anything under `outbox/` |

Files both agents of a stage may touch, under an append-only rule (add new members, never change or reorder existing ones):

- Test helpers are not shared: each task adds its own helper class under `src/test/java/.../support/` (for example `TransferActions` for 5.0, `HistoryActions` for 7.0) and does not edit another task's helper file.
- `GlobalExceptionHandler` — stage 5: each adds handlers for its own exceptions
- `src/main/resources/application.yml` — stage 7: 8.0 adds outbox settings, 9.0 adds management and logging settings

### Rules for Parallel Agents

The generic rules for agents, models, dispatch specs and worker lifecycle are in [`docs/orchestration.md`](../docs/orchestration.md). Where they conflict with this file, that file wins. The rules below are specific to this plan.

- **Worktrees:** sequential stages run in the current worktree. In the two parallel stages each task gets its own child worktree and a branch named `task-N.0`, because the pair shares append-only files and each runs its own Maven build.
- **Merge order:** within a parallel stage, merge the lower-numbered task first, then rebase the other onto the main branch and merge it.
- **Stage gate:** after the last merge of a stage, a worker runs `./mvnw verify` on the main branch and reports the result. The coordinator does not run builds. The next stage starts only if the gate passes, including JaCoCo.
- **Active workers:** at most three at once, counting implementers and reviewers.
- **Migrations:** a task that needs a new Flyway migration numbers it from its task number (task 7.0 uses `V70__...`, task 8.0 uses `V80__...`), so two agents never pick the same version.
- **Checkboxes:** an agent ticks subtasks only in its own task file. Only the coordinator ticks tasks in this file.
- **Shared decisions:** an agent that finds the tech spec wrong or incomplete reports it and does not improvise. The coordinator updates `techspec.md` before anyone continues.

### Agent and Model per Task

Assigned from the routing table in `docs/orchestration.md` section 2. Every task here is verified by `./mvnw verify` against a real PostgreSQL in Testcontainers, so an implementer must be able to reach Docker. Probed on 2026-10-11: headless Codex (`codex exec -s workspace-write`) is denied the Docker socket, while a Codex worker started through Orca (`worker-start --agent codex`) reaches it. Codex therefore implements only as a supervised Orca worker, and headless Codex is used only for read-only review.

| Task | Kind | Implementer | Independent review (read-only) |
|---|---|---|---|
| 1.0 Scaffold | New logic (tooling) | Droid `glm-5.3` `-r high` | — |
| 2.0 Schema and domain model | Critical: schema, money, migration | Droid `gpt-6-sol` `-r high` | Droid `glm-5.3` |
| 3.0 Create wallet and balance | New logic; sets the patterns later tasks copy | Droid `glm-5.3` `-r high` | Codex |
| 4.0 Deposit and withdrawal | Critical: money, concurrency | Codex (Orca worker) | Droid `glm-5.3` |
| 5.0 Transfer | Critical: money, concurrency | Codex (Orca worker) | Droid `glm-5.3` |
| 7.0 Historical balance and ledger | New logic | Droid `glm-5.3` `-r high` | Codex |
| 6.0 Idempotent replay | Critical: money, concurrency | Codex (Orca worker) | Droid `glm-5.3` |
| 8.0 Outbox | New logic | Droid `glm-5.3` `-r high` | Codex |
| 9.0 Observability | New logic | Droid `glm-5.3` `-r high` | — |
| 10.0 Packaging and docs | New logic and documentation | Droid `glm-5.3` `-r high` | — |
| Stage gates | Mechanical | Droid `glm-5.3-flash` `-r high` | — |

- A reviewer is always from a different provider than the author.
- Review findings come back in the reviewer's summary and are fixed by the original implementer in the same session, not by a new worker.
- Two attempts without progress on the same failure escalate to Droid `claude-opus-5-5` `-r high`, carrying the reproduction and the hypothesis.

## Dependencies and Coverage

| Task | Depends on | Business rules covered | Estimate |
|---|---|---|---|
| 1.0 | — | — | 0.75h |
| 2.0 | 1.0 | BR-03, BR-04, BR-18 | 1h |
| 3.0 | 2.0 | BR-01, BR-02, BR-11 (balance) | 0.75h |
| 4.0 | 3.0 | BR-05, BR-06, BR-07, BR-11, BR-12, BR-17, BR-19, BR-20, BR-24, BR-27, BR-29 | 2h |
| 5.0 | 4.0 | BR-08, BR-09, BR-10, BR-11 (transfer), BR-17 (transfer), BR-19 (randomised), BR-25 | 1.25h |
| 6.0 | 5.0, 7.0 | BR-21, BR-22, BR-23, BR-26 | 1h |
| 7.0 | 4.0 | BR-13, BR-14, BR-15, BR-16 | 1h |
| 8.0 | 6.0 | BR-28 | 0.75h |
| 9.0 | 6.0 | — | 0.5h |
| 10.0 | all | — | 1h |

Task 6.0 needs only 5.0 for its behaviour. It also waits for 7.0 because both edit `WalletController`.

## Cutting Scope

If time runs short, drop features whole, in this order: task 8.0, then the ledger endpoint in task 7.0 (subtasks 7.5 and 7.6). Record each as a trade-off in the README. Tests and readability are never what is cut.

If 8.0 is cut, stage 7 is 9.0 alone.

## Current State

The coordinator records one line per accepted delivery here: task, agent and model used, verification result, and anything left open. Workers do not write to this section.

- Environment (2026-10-11): only JDK 26.0.2 is installed locally, not JDK 25. The build targets Java 25 (`maven.compiler.release=25`) and runs on JDK 26; the Docker image in task 10.0 uses a JDK 25 runtime.
- 1.0 accepted — Droid `glm-5.3` `-r high`; `./mvnw verify` BUILD SUCCESS, commit `35a646c`. Spring Boot 4.1.1, Spotless 3.10.4, JaCoCo 0.8.15, Testcontainers 2.0.5. Nothing open.
- 2.0 accepted — Droid `gpt-6-sol` `-r high`, reviewed by Droid `glm-5.3`; `./mvnw verify` BUILD SUCCESS, 16 tests, 96.97% line / 100% branch, commits `a4549ee` and `0dfec46`. Added `spring-boot-flyway` (Boot 4 needs it for Flyway to run before JPA validation). Domain types added beyond the task list: `Balance`, `SignedAmount`, `OperationType`. Nothing open.
- Orca Run `run_e644aeeff8a8` created for supervised Codex workers.
- 3.0 accepted — Droid `glm-5.3` `-r high`, reviewed by headless Codex (2 blockers and 8 pattern fixes, all applied); `./mvnw verify` BUILD SUCCESS in 11.7s, 35 tests, 98.40% line / 94.44% branch, commits `0d5334c` and `f42b534`. Test helpers are now small classes by concern in `support/` (`WalletActions`, `WalletAssertions`, `TestClock`, `TestDatabase`), used by static import. Open, minor: `LedgerImmutabilityTest` still has a harmless `@DirtiesContext`; task 4.0 removes it.
- Codex via Orca is blocked at startup by its update prompt (0.157.0 → 0.162.1); Orca will not send input to a blocked agent prompt. The user must update Codex or dismiss the prompt. Until then critical tasks use the second choice, Droid `gpt-6-sol`.
- 4.0 accepted — Droid `gpt-6-sol` `-r high`, reviewed by Droid `glm-5.3` (money path confirmed correct; 6 quality fixes applied); `./mvnw verify` BUILD SUCCESS, 58 tests, 97.82% line / 92.50% branch, ConcurrencyTest 10/10 then 5/5 consecutive passes, commits `4bbcf9f` and `6d04ebb`. Finding: the JPA lock-timeout hint is ignored for positive values on PostgreSQL, so `SET LOCAL lock_timeout = '2s'` is used. `OperationId` domain type added. Nothing open.
- 5.0 accepted and merged to main (fast-forward) — Droid `gpt-6-sol` `-r high`, reviewed by Droid `glm-5.3` (locking, atomicity and arithmetic confirmed correct; 5 fixes applied); `./mvnw verify` BUILD SUCCESS on the branch, 97.64% line / 90.38% branch, ConcurrencyTest 10/10 then 5/5, commits `ec67ce9` and `3b4865e`. Nothing open.
- 7.0 accepted and merged to main (fast-forward after rebase, no conflicts) — Droid `glm-5.3` `-r high`, reviewed by headless Codex (1 blocker: nanosecond cutoff bound to a microsecond column, fixed with truncation and a regression test; 2 other fixes applied; BR-11 labels kept by coordinator decision), commits `e916830` and `fe7a76d`. Page size defaults to 20, capped at 100. Nothing open.
- Stage 5 gate passed: `./mvnw verify` BUILD SUCCESS on the rebased head that is now main (`fe7a76d`), 92 tests, 97.99% line / 92.42% branch. Worktrees and branches removed.
- 6.0 accepted — Droid `gpt-6-sol` `-r high`, reviewed by Droid `glm-5.3` (replay, transaction boundaries and concurrency confirmed correct; 4 tests added); `./mvnw verify` BUILD SUCCESS, 102 tests, 97.70% line / 89.74% branch, ConcurrencyTest 10/10, commits `a2a4615` and `f512d28`. Decision: the operation row is inserted before the wallet lock; tech spec section 4 updated to match. Open, minor: `LedgerService.replay` takes the fingerprint as a bare `String`; left for the readability pass in 10.0.
- 8.0 accepted and merged to main (fast-forward) — Droid `glm-5.3` `-r high`, reviewed by headless Codex (money path confirmed intact; 10 fixes applied, mostly test independence); `./mvnw verify` BUILD SUCCESS on the branch, 113 tests, 96.18% line / 90.00% branch, ConcurrencyTest 5/5, commits `76bc257` and `83291f3`. Publish delay 2s, batch 50, scheduling off in tests by property.
- 9.0 accepted and merged to main (fast-forward after rebase; one keep-both conflict in `application.yml`) — Droid `glm-5.3` `-r high`, no review planned; commits `5bcb9f6`, `9364ffa`, `ceca6f4`. Added `micrometer-registry-prometheus` (BOM-managed) by coordinator decision. Pool size 10, graceful shutdown 30s, health details hidden.
- Stage 7 gate passed: `./mvnw verify` BUILD SUCCESS in 28.4s on the rebased head that is now main (`ceca6f4`), 127 tests, 96.34% line / 87.50% branch, one Spring context. Worktrees and branches removed.
- 10.0 accepted — Droid `glm-5.3` `-r high`; compose stack built on `eclipse-temurin:25` images and exercised with curl (all seven endpoints, a rejection, an idempotent replay, readiness, Prometheus, API docs), then torn down; README written; tech spec reconciled with the code; readability pass added the `RequestFingerprint` domain type; `./mvnw verify` BUILD SUCCESS, 127 tests, 96.39% line / 87.50% branch, ConcurrencyTest 5/5, commits `e11e177` and `597873e`. Open, minor: metric names are registered with underscores in `OperationMetrics` and dots in `OutboxMetrics` (served output is identical).
- All ten tasks accepted. Final independent gate passed (Droid `glm-5.3-flash`, on `aead714`): `./mvnw verify` BUILD SUCCESS, 127 tests, 0 failures, 0 skipped; 96.39% line / 87.50% branch recomputed from `jacoco.csv`; every rule BR-01 to BR-29 has at least one test; no sleeps, disabled tests or mocks in `src/test`; `docker compose config` valid.

- Task 11.0 (added after the plan, at the author's request): manual test console, one static page at `/` (`static/index.html`), no dependency or Java change. Droid `glm-5.3`, reviewed by Codex. The first delivery was verified only with curl and did not work in a browser (undeclared element references, wallet ids stored with quotes); fixed in the same session and accepted on `dcb5b03` after a headless-Chrome run of the served page passed 15/15 steps with no page or console error. `./mvnw verify` BUILD SUCCESS, 128 tests, 96.39% line / 87.50% branch.

## Rules for Every Task

- Follow `techspec.md` section 9.1: the code is written for humans to read.
- Follow `techspec.md` section 9.3 for every test: one rule and one behaviour per test, rule id in the display name, given/when/then, independent, deterministic.
- A task is finished only when the tests for its business rules exist and pass, the whole suite passes, and the JaCoCo gate (90% line, 85% branch) passes.
