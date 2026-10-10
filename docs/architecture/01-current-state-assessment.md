# 01 — Current State Assessment

## 1. Source project: Open4Talk (`open4talk_be_v2`) — read-only

Evidence: `docs/architecture-assessment/{01..08}` in the source repo, read directly for this task.

### 1.1 Stack — **CONFIRMED**

- NestJS (Fastify adapter), TypeScript, TypeORM 0.3.x, `synchronize: false` (migration-driven).
- PostgreSQL (Cloud SQL in production).
- Redis + BullMQ for background jobs (`media` queue; `nlp`/`learning`/`notify` queues in `events`).
- Google Cloud Storage for media/avatars. Elasticsearch for transcript/segment search.
- External providers: OpenAI, Anthropic, Azure Speech SDK, Clerk, Google OAuth, Telegram, PostHog.
- yt-dlp + fluent-ffmpeg for YouTube ingestion → HLS transcoding, invoked from a BullMQ worker
  (`media.processor.ts`) — **UNKNOWN** whether this worker runs in the same Cloud Run process as the
  HTTP API or a separate deployment (flagged in source `07-technical-debt.md` as an operational risk
  requiring infra-owner confirmation).
- No CI pipeline, no automated unit tests beyond a default e2e stub — **CONFIRMED**.

### 1.2 Business capabilities (module inventory) — **CONFIRMED** (see source `02-module-inventory.md`)

| Domain                   | Representative modules                                                                                                    | Entities                                                                                                                                                           |
| ------------------------ | ------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| Identity & User          | `auth`, `users`                                                                                                           | `User`, `Friend`                                                                                                                                                   |
| Learning / Curriculum    | `topics`, `questions`, `exercises`, `vocabulary`, `sample-sentence`, `dictionary`, `suggestions`                          | `Topic`, `Question`, `Exercise`, `ClozeSet`, `ClozeItem`, `SpeakingLine`, `ExerciseAttempt`, `Vocabulary`, `SampleSentence`, `Dictionary`                          |
| AI Practice / Evaluation | `ai-sessions`, `ai-session-question`, `ai-prompt-templates`, `user-prompt-cache`, `ielts-evaluation`, `speaking-analysis` | `AiSession`, `AiSessionQuestion`, `AiPromptTemplate`, `UserPromptCache`, `Suggestion`                                                                              |
| Assessment / Progress    | `mock-test`, `user-answers`, `user-progress`, `user-question-interaction`, `user-vocabulary`                              | `MockTest`, `MockTestAnswer`, `UserAnswer`, `UserProgress`, `UserQuestionInteraction`, `UserVocabulary`                                                            |
| Social                   | `comment`, `shared-answers`, `feedbacks`                                                                                  | `CommentEntity`, `SharedAnswer`, `Feedback`                                                                                                                        |
| Media Platform           | `media`, `transcript`, `search`                                                                                           | `MediaAsset`, `MediaPlay`, `MediaFavorite`, `MediaVocabSuggestion`, `MediaAssessment`, `Transcript`, `SearchSegment`, `DictionaryHeadword`, `DictionaryDefinition` |
| Learning/SRS             | `learning` (`api/learning`)                                                                                               | `SavedWord`, `ReviewLog`, `VideoProgress`                                                                                                                          |
| Payments                 | `vqr` (VietQR)                                                                                                            | **UNKNOWN schema** — no entity confirmed in this pass                                                                                                              |
| Cross-cutting            | `elastic`, `posthog`, `health`, `share`, `web_socket`, `events`, `db`                                                     | n/a                                                                                                                                                                |

`users`/`UserService` is a de-facto shared kernel consumed by nearly every module — **CONFIRMED**,
this is the primary seam for an Identity boundary.

### 1.3 Known anomalies / risks carried forward — **CONFIRMED unless noted**

1. **9 entities exist in code but are absent from `data-source.ts`'s registered entity list**
   (`ClozeSet`, `ClozeItem`, `SpeakingLine`, `ExerciseAttempt`, `SearchSegment`, `Transcript`,
   `SavedWord`, `ReviewLog`, `VideoProgress`). Whether their tables exist in production is
   **UNKNOWN** — requires a live `\dt` schema diff against Cloud SQL before any JPA entity is
   generated for these. **Do not port these 9 tables' schema as fact; re-verify first.**
2. GCS service-account key file committed to repo root — **Critical**, must be rotated independent
   of migration timing. Not applicable to the target repo (no secret was copied here).
3. `GET /auth/env-debug` debug endpoint — **High**, do not port.
4. No default-deny auth guard (`APP_GUARD` not configured); ~21/30 controllers have guards —
   **High**, must design the Java equivalent as default-deny (e.g. Spring Security filter chain
   denying by default, `@PermitAll`/public allow-list opt-out), not opt-in per controller.
5. `vqr` (payment) module has no confirmed webhook signature validation — **Critical, pending
   verification** by the source team; out of scope to "fix" here since the source is read-only, but
   the Java Payment service design (doc 03) mandates signature verification as a hard requirement.
6. No transaction boundaries confirmed around multi-entity writes in `media.service.ts` (2253
   lines, largest file in the repo) — **Potential**, not confirmed; must not assume current
   behavior is transactionally safe when designing the Java equivalent.
7. Possible conceptual duplication: `dictionary.Dictionary` vs. `media.DictionaryHeadword/Definition`
   — **UNKNOWN**, needs diffing before deciding to unify in the target data model.

## 2. Target project: `JAVA-ecommerce-backend-api-MEMBER` — inspected directly

### 2.1 Current architecture — **CONFIRMED**

- Maven multi-module aggregator (`pom.xml`, `packaging=pom`), Java **21**, Spring Boot parent
  **3.5.6**.
- `myshop-framework` (packaging `jar`): shared library — MySQL (`mysql-connector-j`), **MyBatis-Plus
  3.5.9** (not JPA/Hibernate), Lombok, Bean Validation. Contains `entity/`, `mapper/`, `service/`,
  `serviceimpl/`, `vo/` layering under `com.myshop.modules.product`, plus `com.myshop.common` and
  `com.myshop.cache`.
- `myshop-module-manager` (packaging `jar`, Spring Boot app): REST API (`ManagerApplicationApi`),
  port `1122`, depends on `myshop-framework`, Spring Boot Actuator + Micrometer Prometheus,
  springdoc-openapi (Swagger UI). `config/`, `controller/`, `modules/product/` packages.
- Persistence: **MySQL 8.0** via Docker Compose (`myshop-mysql`, mapped `3307:3306`), connected at
  `jdbc:mysql://localhost:3306/myshop_manager` with `mybatis-plus` logical-delete convention
  (`deleteFlag` column). No Postgres, no JPA/Hibernate anywhere in this repo today.
- No Spring Security, no JWT, no OAuth2, no message broker (RabbitMQ/Kafka), no Redis, no GCS
  client, no Elasticsearch client, no AI SDKs present in any `pom.xml` today — **CONFIRMED by
  reading both module POMs in full**.
- Monitoring: Prometheus (`:9090`) + Grafana (`:3000`) + Node Exporter (`:9100`), scrape target
  `host.docker.internal:1122/actuator/prometheus` — reusable as-is for any new Spring Boot module.
- Domain: an e-commerce **product catalog** (`myshop.modules.product`), i.e. unrelated business
  domain to Open4Talk's IELTS/media-learning platform.
- No test suite inspected beyond default Maven layout; no CI config found in the repository root.

### 2.2 Reusable infrastructure

- Maven aggregator/module pattern, Lombok + validation conventions, Actuator/Prometheus/Grafana
  stack, Docker Compose pattern, springdoc-openapi setup.

### 2.3 NOT reusable as-is (must be newly introduced, not assumed)

- Persistence stack (MyBatis-Plus + MySQL) is a **deliberate convention for the product-catalog
  domain**. Open4Talk's source-of-truth schema is PostgreSQL with 38 TypeORM entities and
  Postgres-native enum/array columns. See §4 for the resulting decision that blocks Phase G.
- Security (none exists yet) — must be added from scratch for any Identity/Auth-bearing service.
- Messaging, cache, object storage, search, AI SDKs — none exist yet; each must be justified
  per-service per `05-technology-stack-decisions.md`, not added speculatively.

## 3. Confirmed vs. Inferred vs. Unknown — summary legend used throughout this doc set

- **CONFIRMED**: verified by directly reading source code/config (either repo).
- **INFERRED**: architecturally reasonable conclusion from confirmed evidence, not directly proven.
- **UNKNOWN**: requires further investigation (e.g., live DB access, reading a full file not yet
  opened) before being treated as fact.

## 4. Blocking decision before Phase G (implementation)

The target project's persistence convention (**MySQL + MyBatis-Plus**, logical-delete flag column,
no JPA) is incompatible, as a direct reuse target, with Open4Talk's actual data model
(**PostgreSQL**, Postgres-native enums and `text[]` arrays, UUID PKs, 38 entities with non-trivial
relations). Two defensible paths exist and **must be chosen by a human decision-maker, not assumed**:

- **Option A — New services, new database(s).** Add new Maven modules (e.g. `myshop-identity`,
  `myshop-learning`, …) that each bring their own PostgreSQL schema/dependency (Spring Data JPA +
  Postgres driver), reusing only the _aggregator, Actuator/Prometheus/Grafana, and Docker Compose_
  conventions from the existing project. The existing `myshop-framework`/MyBatis-Plus/MySQL stack
  is left untouched for the product-catalog domain. **Recommended** — avoids forcing an unrelated
  domain (IELTS learning) onto an unrelated schema convention (MySQL/MyBatis) chosen for a
  different business, and avoids any risk to the existing e-commerce functionality.
- **Option B — Force Open4Talk's data model onto MySQL + MyBatis-Plus.** Rejected unless explicitly
  requested: would require re-deriving every Postgres-specific construct (native enums, array
  columns, UUID generation strategy) for MySQL, duplicating the 9-entity schema-registry risk in a
  different ORM, and contradicts rule "do not invent database columns" since the live Postgres
  schema has not been verified yet (§1.3 item 1).

**This documentation set proceeds on the assumption of Option A** (new modules/new schemas,
PostgreSQL + Spring Data JPA, reusing only cross-cutting infra), because it is the only option
that does not require fabricating schema facts or silently changing the existing product-catalog
module's database engine. If the user intends Option B, the `06-migration-roadmap.md` Phase 0 must
be re-scoped before any code is written.
