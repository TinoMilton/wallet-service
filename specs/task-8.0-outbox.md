# Task 8.0: Transactional Outbox and Publisher

<critical>Read the prd.md and techspec.md files in this folder. If you do not read these files, your task will be invalidated.</critical>

## Overview

Announce every successful money operation as an event. The event is written in the same transaction as the ledger entries, and a scheduled publisher relays it afterwards.

This task is the first to be cut if time is short. If it is cut, the `outbox_event` table stays unused and the omission is recorded as a trade-off.

**Execution:** stage 7, in parallel with task 9.0. Starts when 6.0 is merged. Follow the file ownership and parallel-agent rules in `tasks.md`: do not edit the controllers, anything under `observability/`, or `logback-spring.xml`.

<skills>
### Standard Skills Compliance

None. The project has no `.claude/skills` directory.
</skills>

<requirements>
- Each successful deposit, withdrawal and transfer inserts exactly one `outbox_event` in the same transaction as its ledger entries.
- A rejected operation inserts none. An idempotent replay inserts none.
- Event types are `FundsDeposited`, `FundsWithdrawn` and `FundsTransferred`. The payload carries the operation id, wallet ids, amount and time.
- `OutboxPublisher` runs on a fixed delay, selects unpublished events in `occurred_at` order with `FOR UPDATE SKIP LOCKED`, relays each and sets `published_at`.
- The relay is an interface with one implementation that writes a structured log line.
- An event whose relay fails stays unpublished and is tried again on the next run.
- Wallet creation emits no event.
- This task owns the outbox metrics: the gauges `wallet_outbox_unpublished` and `wallet_outbox_lag_seconds`, registered in `OutboxMetrics`.
</requirements>

## Subtasks

- [ ] 8.1 Add the event-recording step to the write flow in `LedgerService`
- [ ] 8.2 Define the event payloads
- [ ] 8.3 Implement the `EventRelay` interface and its logging implementation
- [ ] 8.4 Implement `OutboxPublisher` with the skip-locked query
- [ ] 8.5 Register the two outbox gauges in `OutboxMetrics`
- [ ] 8.6 Write the tests for BR-28, the publisher and the gauges

## Implementation Details

- Outbox design: `techspec.md` section 7, "Outbox".
- Table: section 5.
- Where the step sits in the write flow: section 4.
- Why commands stay synchronous: section 2.

## Success Criteria

- BR-28 has passing dedicated tests for the success and the rejection case.
- The publisher test triggers a publishing run directly instead of waiting for the schedule.
- The whole suite and the JaCoCo gate pass.

## Task Tests

- [ ] Unit tests: none
- [ ] Integration tests:
  - BR-28 a successful deposit emits exactly one event with the operation id
  - BR-28 a successful transfer emits exactly one event
  - BR-28 a rejected withdrawal emits no event
  - BR-28 an idempotent replay emits no second event
  - A publishing run marks unpublished events as published, in order
  - An event whose relay fails stays unpublished
  - `wallet_outbox_unpublished` reports the number of unpublished events
- [ ] E2E tests: not applicable

<critical>ALWAYS CREATE AND RUN THE TASK'S TESTS BEFORE CONSIDERING IT FINISHED</critical>

## Relevant Files

- `src/main/java/com/recargapay/wallet/service/LedgerService.java`
- `src/main/java/com/recargapay/wallet/outbox/OutboxPublisher.java`
- `src/main/java/com/recargapay/wallet/outbox/EventRelay.java`
- `src/main/java/com/recargapay/wallet/outbox/LoggingEventRelay.java`
- `src/main/java/com/recargapay/wallet/outbox/OutboxMetrics.java`
- `src/main/java/com/recargapay/wallet/outbox/events/`
- `src/main/java/com/recargapay/wallet/repository/OutboxEventRepository.java`
- `src/test/java/com/recargapay/wallet/outbox/OutboxEventTest.java`
- `src/test/java/com/recargapay/wallet/outbox/OutboxPublisherTest.java`
