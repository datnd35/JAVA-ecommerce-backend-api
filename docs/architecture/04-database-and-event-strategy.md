# 04 — Database and Event Strategy

## 1. Guiding principle

One PostgreSQL cluster (e.g. the existing/managed Cloud SQL instance, or a new one if required by
the hosting decision), with **separate schema/database and separate DB user/credentials per
service** — not separate physical servers by default, and not a single shared schema. This
satisfies ownership isolation without multiplying operational cost prematurely.

## 2. Ownership rules

- Each entity/table has exactly **one** owning service (see `03-service-boundaries-and-ownership.md`).
- No service holds a JPA `@ManyToOne`/`@JoinColumn` into another service's table. Cross-service
  references are plain UUID columns (e.g. `UserAnswer.userId`), validated at write time via a
  synchronous call to the owning service or trusted from an authenticated JWT claim, not via FK.
- No shared Postgres schema permissions across services; grant only the owning service's DB role
  write access to its schema.

## 3. Sync vs. async — decision rule

- **Use synchronous HTTP** when the caller needs the answer to complete the current request, the
  data must be the latest version, and the callee is fast/reliable (e.g. Gateway verifying JWT
  against Identity's public key — ideally this is local, no call at all).
- **Use events** when: the operation is long-running (media transcode), multiple services must
  react to one business fact (payment confirmed → entitlement update), or retry/backoff is
  acceptable (evaluation scoring in async mode).
- **Must remain transactional within one service** (not split across services):
  `UserAnswer` + `UserProgress` + `UserQuestionInteraction` updates from a single answer submission
  (all owned by Learning) — this must be wrapped in one Spring `@Transactional` boundary, which is
  a **new** guarantee not confirmed to exist in the source (treated as a fix, not a port).
- **Eventual consistency acceptable**: entitlement update after payment confirmation; search index
  (Elasticsearch) rebuild after transcript ingestion; social fan-out of `AnswerSubmitted`.

## 4. Transactional outbox pattern

For every service that must publish an event as a side effect of a DB write (Payment →
`PaymentConfirmed`, Media Worker → `MediaIngestionCompleted`, Learning → `AnswerSubmitted`):

- Write the business row and an `outbox_event` row in the **same local transaction**.
- A relay process (scheduled poller or CDC) publishes outbox rows to the broker and marks them
  dispatched — avoids the dual-write problem (DB commit succeeds, broker publish fails, or vice
  versa) without a distributed transaction.

## 5. Idempotent consumers

- Every consumer stores a `processed_message_id` (or equivalent) uniqueness constraint before
  applying side effects, so redelivery (at-least-once broker semantics) does not double-apply
  (e.g. Identity applying `PaymentConfirmed` twice must not double-grant premium duration).
- Payment webhook ingestion itself must be idempotent on the bank's transaction reference
  (unique constraint), independent of the outbox/event idempotency above.

## 6. Retry / dead-letter / reconciliation

- Standard retry with capped exponential backoff at the consumer; after N failures, route to a
  dead-letter queue/table for manual or scheduled reconciliation.
- Payment → Identity entitlement update: if it fails permanently, a scheduled reconciliation job
  in Identity (or a small ops job) cross-checks `PaymentConfirmed` outbox history against
  entitlement state — no distributed transaction, explicit reconciliation instead.

## 7. The 9 "missing from registry" entities — investigation required before migration

`ClozeSet`, `ClozeItem`, `SpeakingLine`, `ExerciseAttempt`, `SearchSegment`, `Transcript`,
`SavedWord`, `ReviewLog`, `VideoProgress` are declared with `@Entity()` in source but **not** listed
in `data-source.ts`'s TypeORM entities array, and no migration file name references their
tables (per source `04-database-design.md`). Before creating any JPA entity/migration for these in
the Java services:

1. Obtain read-only access to the production/staging Postgres instance.
2. Run `\dt` and compare against both the 29 registered + 9 unregistered entity names.
3. If tables exist: capture the actual `\d+ <table>` output as the schema source of truth (do not
   trust TypeScript entity field types/nullability alone).
4. If tables do not exist: treat these as **net-new** schema to design for the Java service, not a
   migration of existing data.
   This is a hard prerequisite for Learning and Media service schema design and is called out again
   in `06-migration-roadmap.md` Phase 0.

## 8. Distributed transactions — explicitly rejected as default

Per task constraints and sound practice: no two-phase commit / distributed transaction between
services is used as the default solution for any cross-service business operation in this design.
Every cross-service consistency need above is handled via outbox + idempotent consumer +
reconciliation instead.
