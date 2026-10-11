# Multi-agent Orchestration (Orca + Droid/GLM + Codex + Claude)

Rules for the **coordinator**. Workers do not read this file: everything a worker needs is in `AGENTS.md` and in its dispatch spec. Goal: the most accepted work without exhausting the 5-hour window or the weekly limit.

Brought over from `ContainerApp/docs/orquestracao.md` (rules as of 2026-10-07) and adapted to this project's commands. Commands were checked on Orca 1.4.219. Before coordinating, load the guide for the installed version with `orca skills get orchestration`; if it differs from this file, the guide wins.

## 1. Roles

- **Coordinator:** decomposes, dispatches, waits, integrates the result. It does not read code, run builds or tests, or review diffs; those are worker tasks. The coordinator's context holds only specs, `worker_done` summaries and decisions.
- **Worker:** carries out one scoped task and reports in about 10 lines at most.

## 2. Which Agent and Which Model

The aim of the balancing: **Claude is the scarce resource.** It keeps coordination and escalation; the volume goes to Droid (GLM models) and Codex.

| Kind of task | 1st choice | 2nd choice | Claude (subscription)? |
|---|---|---|---|
| Mechanical work, and implementation that **copies an already approved pattern** (a sibling of an existing endpoint or test) | Droid `glm-5.3-flash` `-r high` (0.06×) | Droid `glm-5.3` | no |
| Implementation with new logic: services, endpoints, integration | Droid `glm-5.3` `-r high` (0.56×) | Droid `gpt-6-sol` (0.8×) or Codex | no |
| Critical code: schema, money, concurrency, migrations | Codex (agent default) | Droid `gpt-6-sol` `-r high` | no |
| Independent review (read-only) | A **different** provider from the author: GLM author → Codex or Droid `gpt-6-sol`; Codex/GPT author → Droid `glm-5.3` | Droid `grok-4.7` (0.8×) | no |
| Escalation after 2 attempts without progress | Droid `claude-opus-5-5` `-r high` (1.6×, Factory quota) | Orca worker `claude-opus-5-5` | only if the Factory quota runs out |
| Architecture and contract decisions between tasks | Coordinator (this session) | — | yes |

Basis for the choice (2026-10-07; redo when Droid's model list changes): multipliers from [docs.factory.com/models](https://docs.factory.com/models) and Terminal-Bench 4.0 / SWE-bench Verified. TB4 points per model available in Droid: Opus 5.5 ~65 (1.6×), GPT-6 Astra 58 (4×), GPT-6 Sol 44–49 (0.8×), GLM-5.3 42 (0.56×), Grok 4.7 38 (0.8×), GLM-5.3-Flash 33 (0.06×), DeepSeek V4.1 Flash 27 (0.12×), Qwen3.8 Max 19 (0.8×), GPT-6 Luna 13 (0.04×), Kimi K3 13 (1.2×), Sonnet 5 12 (0.8×), Gemini 3.1 Pro 4 (0.8×). **Do not use** in Droid: Kimi K3, Qwen3.8 Max, Gemini (all), Sonnet 5, GLM-5.2, Inkling, Nemotron, Mistral, DeepSeek. They cost the same as or more than a better option in the table.

- **Coordinator** = this Claude session. It does not read code or run tests. It compacts before 40% of its context.
- **A Claude Sonnet worker is not the default implementer.** It is used only if Droid and Codex are both out of quota, and that is recorded under "Current State" in `specs/tasks.md`.
- **Escalation:** two attempts without progress on the same failure → move up one row of the table, carrying the reproduction and the hypothesis (never the task from scratch).
- **Codex near the end of the 5-hour window** (`<10%` warning): only closed tasks of up to about 20 thousand tokens; the rest waits for the reset or goes to Droid.
- `high` effort is an exception justified in the spec, except on GLM, where `high` is the normal effort for implementation.

### How to Launch Each One

| Agent | Command | Follow-up in the same context |
|---|---|---|
| Droid (write) | `droid exec --auto medium -m glm-5.3 -r high -f <spec> > <output> 2>&1` in the background | `droid exec --auto medium -s <session_id> -f <spec>` |
| Droid (review) | `droid exec -m <model> -r high -f <spec>` (no `--auto` = read-only) | — |
| Codex (write) | `codex exec -s workspace-write "$(cat <spec>)" < /dev/null` | `codex exec resume --last` |
| Codex (review) | `codex exec -s read-only "$(cat <spec>)" < /dev/null` | — |
| Claude (escalation) | `orca orchestration worker-start --agent claude --model claude-opus-5-5 --effort high ...` | `worker-start --terminal <handle>` |

- Headless Droid and Codex run **outside the Orca supervised Run**: there is no `worker_done`. The result is the final summary in the output file (ask in the spec: "print up to 10 lines..."), and the proof is the verification command. Questions do not reach the coordinator, so the spec must say what to do for each foreseeable doubt ("if you need a new migration or dependency, stop and describe it").
- `--auto medium` is the ceiling. Never `--auto high` and never `--skip-permissions-unsafe`.
- The Codex `workspace-write` sandbox does not open ports. **Anything that needs Docker, including every Testcontainers integration test and `docker compose`, goes to Droid.** Codex keeps code, unit tests and compilation.
- Operating limit: up to three active workers, counting Orca and headless together.
- The requested model does not prove the model used. In Orca, compare `launch.requested` with `launch.effective`; headless, the model is given explicitly with `-m`.

## 3. Dispatch Spec

Every spec is self-sufficient and states:

- **Target:** exact files or modules. Never "explore the repo".
- **Change:** the concrete result.
- **Out of scope:** what not to touch, and who owns it.
- **Done when:** the verification command and its expected output (`./mvnw verify`, `./mvnw test -Dtest=<Class>`...).
- **Inherited context:** the summary of the previous `worker_done`, when there is one. The next worker never rediscovers the state on its own.

A broad request ("improve X") is not dispatched: rewrite it with a scope first. Do not paste the PRD, the tech spec or evidence into the spec; point to the exact section if it is indispensable.

Template:

```text
Task: <id and title>
Target: <exact files>
Change: <result>
Out of scope: <files and decisions that are not yours>
Done when: <command> passes; <evidence>
Context: <summary from the previous worker, max. 10 lines>
Delivery: worker_done with what was done, files, decisions, what is left.
```

Example for this project:

```text
Task: 5.0 — Transfer between wallets
Read specs/task-5.0-transfer.md and the tech spec sections it names.
Target: LedgerService, TransferController, transfer DTOs, TransferTest,
        ConcurrencyTest, AuditInvariantRandomisedTest
Change: POST /api/v1/transfers with ordered locking; BR-08, 09, 10, 11, 17, 19, 25 covered
Out of scope: WalletController, WalletService, LedgerEntryRepository (owned by task 7.0).
        If you need a change there, a new migration or a new dependency, stop and describe it.
Done when: ./mvnw verify passes, including the JaCoCo gate; BR-25 passes on 10 consecutive runs
Context: <summary of task 4.0's delivery>
Delivery: print up to 10 lines: what changed, files, decisions, what is left.
```

## 4. Lifecycle

```bash
orca status --json
orca orchestration run-create --objective "<objective>" --json

# 1. Launch the whole independent wave before waiting (headless: see section 2; Orca only for Claude/Codex TUI workers)
orca orchestration worker-start --spec "<spec>" --worktree current --agent claude --model claude-opus-5-5 --effort high --json

# 2. Wait, blocked. Polling in a loop is forbidden.
orca orchestration check --wait --types "worker_done,escalation,question" --timeout-ms 900000 --json

# 3. For each accepted worker_done, decide the terminal BEFORE the ack: reuse or release
orca orchestration worker-show --dispatch <dispatch_id> --json            # recovers the handle
orca orchestration worker-start --task <next_task> --terminal <handle> --worktree current --json   # follow-up in the same terminal
orca orchestration worker-release --dispatch <dispatch_id> --json         # no immediate follow-up

# 4. Confirm the delivery and go back to waiting
orca orchestration check --ack <delivery_id> --wait --types "worker_done,escalation,question" --timeout-ms 900000 --json
```

- `--worktree current` by default. `new-child` only when two fronts write to the same files or need an isolated database or migrations. In this project that means the two parallel stages in `specs/tasks.md`: their tasks share append-only files and each runs its own Maven build, so each gets its own worktree.
- A follow-up or a fix to a live worker's work goes to the **same terminal**. A new worker only if the terminal died or its context became invalid.
- A finished worker is not kept open "to re-read": release it and read later with `orca orchestration worker-read --dispatch <id> --limit 50`.
- Worker questions: `orca orchestration reply --id <message_id> --body "<answer>" --json`.
- **Stuck worker:** a timeout or an empty wait is a checkpoint, not a failure. After three empty waits, run `orca orchestration worker-list --json` and follow each row's `projection.nextAction`. `worker-stop` only with proof that the agent stopped (`liveness` `exited`, or a transcript without `worker_done`); silence alone does not authorise stopping.
- **Retry of a failure:** always `worker-start --task <task_id> --retry-of <dispatch_id>`, with the same agent and worktree. Never redispatch from scratch.
- The coordinator's turn ends only when `orca orchestration worker-list --terminal-state reclaimable --json` comes back empty.

## 5. Window and Limits

- Before a large queue: check Account Usage in Orca (5-hour window, weekly limit and reset times).
- Heavy work starts right after a window reset, never at the end of one.
- Long queue: alternate providers instead of draining one to zero.
- Fan-out of the same prompt across N worktrees only to compare approaches. Independent tasks = one scoped worker each.
- Operating limit: one coordinator and up to three active workers.
- No session passes 40% of its context: write the state to a file, `/compact`, continue the same dispatch.

## 6. Prohibitions

- Creating a new worker to fix or extend the work of a live worker.
- Using the coordinator to read files, run builds or review diffs.
- Leaving an idle worker open.
- Redispatching a failure without `--retry-of`.
- Replacing a real database test with a mock to save effort.
- Accepting a `worker_done` without the evidence of the verification command.
- Asking for a report in a file. The evidence is code, tests and the `worker_done`; the coordinator records the result in one line under "Current State" in `specs/tasks.md`. An independent review also delivers its findings in the `worker_done` (or as fix tasks), not in `review-*.md` files.
- Spending more than one documentation dispatch per wave. A contract document is only for a rule that cannot be read from the code, and it points to the source file instead of copying it.

## 7. Project Plan

Dependencies, stages, file ownership and the agent assigned to each task are in `specs/tasks.md`. The generic rules for routing, lifecycle and limits are the ones in this file; where they conflict, this file wins.
