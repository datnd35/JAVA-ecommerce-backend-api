# 06 — Migration Roadmap

Each phase is independently verifiable and leaves the project buildable. No phase is marked
complete without its acceptance criteria met and actually run (build/tests), not assumed.

## Phase 0 — Decision + baseline (must happen before any service code)

**Scope**: no source code changes beyond what was done in this pass (`docs/architecture/*`).

- Confirm Option A vs. B from `01-current-state-assessment.md §4` with the project owner.
- Obtain read-only access to Open4Talk's actual Postgres schema; resolve the 9-entity anomaly
  (`04-database-and-event-strategy.md §7`).
- Confirm deployment platform target for new Java services (affects API Gateway decision).
- Confirm Clerk-vs-Google-only decision for Identity.
- **Acceptance criteria**: written answers to the above four points exist (can live in this same
  docs directory as an addendum); `mvn -q -DskipTests validate` still passes at repo root
  (no regression from documentation-only change).

## Phase 1 — Media Worker extraction (highest operational risk reduction)

- New Maven module (e.g. `myshop-media-service`) + `myshop-media-worker`, new Postgres schema,
  RabbitMQ for `MediaIngestionRequested`/`Completed`/`Failed`.
- Decompose the equivalent of `media.service.ts` into ingestion / engagement / recommendation /
  assessment sub-services from day one in Java (do not create one god-service).
- Add Testcontainers-based integration tests for the ingestion → worker → status-update path.
- **Acceptance criteria**: `mvn clean verify` green for the new modules; worker survives a
  simulated restart mid-job without losing the job (retry/idempotency test passes); existing
  `myshop-module-manager` build/tests unaffected.

## Phase 2 — AI Evaluation Service

- New module, provider adapter interfaces for OpenAI/Anthropic/Azure Speech, sync + async modes,
  per-provider timeout/retry/quota.
- Characterization tests capturing current expected behavior patterns (since source has none) —
  write Java tests describing intended behavior explicitly, labeled as **new behavior
  specification**, not as a port of verified legacy behavior.
- **Acceptance criteria**: build green; a scaffold/mock provider adapter clearly labeled
  `@VisibleForTesting`/non-production until a real provider key is wired in — do not ship a
  fake-looking production integration.

## Phase 3 — Identity & User Service

- JWT issuance, Google OAuth exchange, default-deny security filter chain, `GET /internal/users/{id}`.
- Other new services (Media/AI) switch from "no auth" scaffolding to verifying JWTs issued here.
- **Acceptance criteria**: integration test proving a request without a token is rejected by
  default (fixes confirmed source gap #3); login round-trip test against a mocked/sandbox Google
  OAuth endpoint passes.

## Phase 4 — Learning Service

- Largest entity surface; implement after the 9-entity schema ground-truth is resolved (Phase 0).
- Explicit `@Transactional` boundary for answer-submission multi-entity writes (new guarantee).
- **Acceptance criteria**: build green; transaction-boundary test (simulated partial failure rolls
  back all three related writes) passes.

## Phase 5 — Social & Payment

- Social: resolve REST-vs-WebSocket single-source-of-truth question first (Phase 0 addendum item),
  then implement.
- Payment: webhook signature verification is a hard gate — no service is marked done without it.
- **Acceptance criteria**: payment webhook integration test with an invalid signature is rejected;
  duplicate webhook delivery (same bank reference) does not double-process.

## Phase 6 — API Gateway (or finalize managed routing)

- Only implemented if Phase 0 decision selects self-hosted gateway.
- **Acceptance criteria**: routing table test for every service prefix; WebSocket passthrough test.

## Phase 7 — Observability, data migration validation, production readiness

- Extend `docker-compose.yml` and `monitoring/prometheus/prometheus.yml` scrape config for each new
  service (additive, does not change existing `myshop-module-manager` target).
- Add OpenTelemetry tracing once ≥2 services actually call each other over the network.
- Data migration validation: row-count/checksum comparisons between source Postgres and target,
  executed in a non-production environment only.
- **Acceptance criteria**: Grafana shows all new services' `/actuator/prometheus` targets `UP`;
  no production migration is run as part of this phase per task constraints — validation only.

## Cross-cutting rule for every phase

- Reuse existing conventions (Maven aggregator, Lombok+validation, Actuator/Prometheus) where
  applicable; introduce new conventions (JPA, Spring Security, RabbitMQ) only in the new modules.
- Run `mvn clean install -DskipTests` at repo root and the relevant module's tests; report actual
  output, not assumed success.
- Preserve rollback: each new module is additive (new Maven module, new schema) — removing it
  (drop module from root `pom.xml` `<modules>`, drop its schema) fully reverts without touching
  `myshop-framework`/`myshop-module-manager`.
