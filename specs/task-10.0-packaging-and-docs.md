# Task 10.0: Packaging and Documentation

<critical>Read the prd.md and techspec.md files in this folder. If you do not read these files, your task will be invalidated.</critical>

## Overview

Produce the deliverables the assignment asks for: a service that starts with one command, and written documentation of how to run it, why it is designed this way and what was traded off for time.

<skills>
### Standard Skills Compliance

None. The project has no `.claude/skills` directory.
</skills>

<requirements>
- A multi-stage `Dockerfile` builds and runs the service on a JDK 25 runtime image.
- `docker compose up` starts the application and PostgreSQL, with the application waiting for a healthy database.
- The README, in English, contains:
  - What the service does and its endpoints, with one example request per endpoint
  - Prerequisites, and how to install, test and run
  - Design choices, and how they meet each functional and non-functional requirement
  - Every assumption with its rationale
  - Trade-offs and compromises made for time, including anything cut
  - How to read the test report: the business-rule catalogue and its mapping to tests
  - The measured line and branch coverage
  - The time actually spent
- The README states only what was built. Anything in the tech spec that was cut is listed as cut.
- Every command in the README is run on a clean checkout before the task is finished.
</requirements>

## Subtasks

- [x] 10.1 Write the `Dockerfile` and add the application to `docker-compose.yml`
- [x] 10.2 Verify a clean `docker compose up` and exercise each endpoint against it
- [x] 10.3 Write the README sections on running and testing
- [x] 10.4 Write the design-choices, assumptions and trade-offs sections
- [x] 10.5 Review every task for anything cut or changed, and reconcile `techspec.md` and the README with what was built
- [x] 10.6 Run the full suite, record the coverage figures and the time spent
- [x] 10.7 Read the whole codebase once against section 9.1 of the tech spec and fix what does not read well

## Implementation Details

- Deliverables: `Wallet_Service_Assignment.pdf`, "Deliverables".
- Design choices and rejected alternatives: `techspec.md` sections 1 and 2.
- Assumptions: section 10.
- Trade-offs: section 11.
- Availability, built versus documented: section 8.
- Coverage reporting: section 9.6.

## Success Criteria

- On a machine with only Docker, `docker compose up` gives a working service.
- On a machine with Docker and JDK 25, `./mvnw verify` passes.
- Each of the four deliverables in the assignment is present.
- All 29 business rules have a passing test, or the README names the rule's feature as cut.
- The README and `techspec.md` do not contradict the code.

## Task Tests

- [x] Unit tests: none new
- [x] Integration tests: none new; the full suite and the JaCoCo gate pass
- [x] E2E tests: not applicable; the compose stack is checked by hand in subtask 10.2

<critical>ALWAYS CREATE AND RUN THE TASK'S TESTS BEFORE CONSIDERING IT FINISHED</critical>

## Relevant Files

- `Dockerfile`
- `docker-compose.yml`
- `README.md`
- `specs/techspec.md`
- `specs/tasks.md`
