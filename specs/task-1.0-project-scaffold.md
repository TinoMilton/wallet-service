# Task 1.0: Project Scaffold and Build Tooling

<critical>Read the prd.md and techspec.md files in this folder. If you do not read these files, your task will be invalidated.</critical>

## Overview

Create the empty Spring Boot project and all the tooling the later tasks rely on: build, database for tests, coverage gate, formatter and the shared test helpers. No business behaviour is implemented here.

<skills>
### Standard Skills Compliance

None. The project has no `.claude/skills` directory.
</skills>

<requirements>
- Java 25 and Spring Boot 4.1.x, built with Maven. Confirm the exact 4.1.x patch version and record it.
- Base package `com.recargapay.wallet`.
- Dependencies: Spring Web MVC, Validation, Spring Data JPA, PostgreSQL driver, Flyway, Actuator, springdoc-openapi; for tests JUnit 5, Testcontainers (PostgreSQL), REST Assured.
- Virtual threads enabled (`spring.threads.virtual.enabled=true`).
- `spring.jpa.open-in-view=false` and `spring.jpa.hibernate.ddl-auto=validate`.
- JaCoCo produces one merged report for all tests and fails the build below 90% line and 85% branch coverage. Only the `main` class and logic-free configuration are excluded, each with a stated reason.
- One formatter configuration applied and checked by the build.
- A `Clock` bean is the application's only source of time.
- Initialise a git repository with a `.gitignore`.
</requirements>

## Subtasks

- [x] 1.1 Generate the Maven project with the dependencies above and confirm it builds on JDK 25
- [x] 1.2 Add `application.yml` with datasource, JPA, Flyway and virtual-thread settings
- [x] 1.3 Add `docker-compose.yml` with PostgreSQL for local runs
- [x] 1.4 Configure the JaCoCo report, gate and exclusions
- [x] 1.5 Configure the formatter check
- [x] 1.6 Add the `Clock` bean and a controllable test clock
- [x] 1.7 Create the integration test base: Testcontainers PostgreSQL, random port, REST Assured setup
- [x] 1.8 Add a smoke test that the application context starts and the health endpoint answers
- [x] 1.9 Initialise git and make the first commit

## Implementation Details

- Stack: `techspec.md` section 3.
- JPA settings: section 7, "JPA Rules".
- Test infrastructure and determinism: sections 9.3 (rules 4 and 5) and 9.4.
- Coverage gate: section 9.6.
- Formatting: section 9.1.

## Success Criteria

- `./mvnw verify` passes on a clean checkout with only Docker and JDK 25 installed.
- The JaCoCo HTML report is generated at `target/site/jacoco`.
- A deliberately unformatted file fails the build.
- The application starts against the compose database.

## Task Tests

- [x] Unit tests: none
- [x] Integration tests: context starts; `/actuator/health` returns UP
- [x] E2E tests: not applicable

<critical>ALWAYS CREATE AND RUN THE TASK'S TESTS BEFORE CONSIDERING IT FINISHED</critical>

## Relevant Files

- `pom.xml`
- `docker-compose.yml`
- `.gitignore`
- `src/main/java/com/recargapay/wallet/WalletServiceApplication.java`
- `src/main/java/com/recargapay/wallet/config/ClockConfig.java`
- `src/main/resources/application.yml`
- `src/test/java/com/recargapay/wallet/support/IntegrationTest.java`
- `src/test/java/com/recargapay/wallet/support/MutableClock.java`
