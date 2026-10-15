# Repository layout

[← Back to the README](../README.md)

```
├── AGENTS.md                   instructions for the coding agents used on this repository
├── CLAUDE.md                   the same instructions, imported for Claude Code
├── Dockerfile                  multi-stage build: JDK 25 build, JDK 25 JRE runtime, non-root
├── docker-compose.yml          the application and PostgreSQL, with health checks; monitoring and load profiles
├── .github/workflows/ci.yml    GitHub Actions: ./mvnw verify on every push to main
├── docs/                       the guides linked from the README; orchestration.md: how the coding agents were coordinated
├── monitoring/                 Prometheus config, Grafana provisioning and dashboards, the k6 load script
├── pom.xml                     Spring Boot 4.1.1, JaCoCo gate, Spotless
├── src/main/java/com/recargapay/wallet/
│   ├── api/                    controllers, DTOs, GlobalExceptionHandler
│   ├── config/                 clock, Jackson, OpenAPI, scheduling
│   ├── domain/                 entities, Money, WalletId, IdempotencyKey, RequestFingerprint, exceptions
│   ├── observability/          operation metrics and outcome levels, problem and replay counters, request id filter
│   ├── outbox/                 publisher, relay, event payloads, gauges
│   ├── repository/             Spring Data JPA repositories
│   └── service/                WalletService, LedgerService, IdempotentCommandExecutor, RequestFingerprints
├── src/main/resources/
│   ├── application.yml         datasource, virtual threads, actuator, outbox settings
│   ├── db/migration/           Flyway migrations (schema, immutability trigger)
│   └── static/index.html       the manual test console served at /
├── src/test/java/com/recargapay/wallet/    tests by concern, shared helpers in support/
└── specs/                      prd.md, techspec.md, tasks.md, task files
```
