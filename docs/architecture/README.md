# Architecture Documentation Index — Open4Talk → Java Spring Boot Migration

This directory documents the evidence-based plan to migrate business capabilities from the
**Open4Talk** NestJS backend (`/Users/macbookairm1/Desktop/MY_PROJECTS/open4talk_be_v2`, read-only
source, not modified) into this repository (`JAVA-ecommerce-backend-api-MEMBER`).

> **Status: Analysis & documentation complete (Phase A–F). Code implementation (Phase G) is
> intentionally NOT started yet** — see `01-current-state-assessment.md §4` for the blocking
> decision that must be made first (database engine / persistence framework conflict between the
> two projects). No service scaffolding, dependency, or entity has been added to `myshop-*` modules
> as part of this pass, so the project remains exactly as buildable as before.

## Documents

| #   | File                                                                                 | Purpose                                                                                                                                                 |
| --- | ------------------------------------------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 01  | [01-current-state-assessment.md](./01-current-state-assessment.md)                   | Source (Open4Talk) and target (`myshop-*`) architecture, confirmed vs. inferred vs. unknown, and the key compatibility conflict blocking implementation |
| 02  | [02-target-microservices-architecture.md](./02-target-microservices-architecture.md) | Candidate service boundaries, diagrams, and rationale                                                                                                   |
| 03  | [03-service-boundaries-and-ownership.md](./03-service-boundaries-and-ownership.md)   | Per-service responsibility, entity/table ownership, API surface, sync/async dependencies                                                                |
| 04  | [04-database-and-event-strategy.md](./04-database-and-event-strategy.md)             | Data ownership, outbox pattern, idempotency, consistency model                                                                                          |
| 05  | [05-technology-stack-decisions.md](./05-technology-stack-decisions.md)               | Stack choices and why each dependency is (or isn't) justified                                                                                           |
| 06  | [06-migration-roadmap.md](./06-migration-roadmap.md)                                 | Phased implementation plan, ordered and independently verifiable                                                                                        |
| 07  | [07-risk-register.md](./07-risk-register.md)                                         | Confirmed and potential risks, severity, required action before/while migrating                                                                         |

## How this maps to the source assessment

The source repo already contains a prior read-only architecture assessment at
`docs/architecture-assessment/` (files `01`–`07` + `08-microservices-architecture-design.md` +
`README.md`). That assessment is **reused as primary evidence** here (cited throughout), not
re-derived from scratch, because it was produced from direct source-code inspection. Where this
documentation set disagrees with or adds to that assessment (e.g. because of the target project's
actual Maven/DB stack), the disagreement is called out explicitly.
