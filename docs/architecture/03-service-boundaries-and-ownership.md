# 03 — Service Boundaries and Ownership

For each candidate service: responsibility, owned entities, API surface (indicative — not a
verified contract, since the source DTOs were not opened in full), dependencies, and migration
priority/complexity. All entity lists are **CONFIRMED present in source code**; exact columns are
**UNKNOWN** until the live schema is diffed (see `01-current-state-assessment.md §1.3`).

## Identity & User Service

- **Bounded context**: authentication, user profile, subscription/entitlement flags.
- **Owned entities/tables**: `User`, `Friend`.
- **Public API (indicative)**: `POST /auth/google`, `POST /auth/refresh`, `GET /users/me`,
  `PATCH /users/me`, internal `GET /internal/users/{id}` for other services.
- **Sync dependencies**: none inbound required; other services call Identity read-only for profile
  enrichment or JWT public-key verification.
- **Async events published**: `UserCreated`, `EntitlementUpdated`.
- **Async events consumed**: `PaymentConfirmed` (from Payment) → update entitlement.
- **External integrations**: Google OAuth, optionally Clerk (decision pending).
- **AuthZ**: default-deny filter chain; replace ad hoc `roles: string[]` with an explicit policy
  model — do not port the denormalized array as-is without a decision review.
- **Failure handling**: idempotent `UserCreated` on duplicate OAuth callback (unique email
  constraint); JWT verification must not require a DB round trip (public key / shared secret only).
- **Priority/complexity**: High priority (shared kernel), Medium-High complexity (dual-IdP legacy
  behavior to decide on).

## Learning Service

- **Bounded context**: curriculum content, practice attempts, progress.
- **Owned entities**: `Topic`, `Question`, `Exercise`, `ClozeSet`, `ClozeItem`, `SpeakingLine`,
  `ExerciseAttempt`, `Vocabulary`, `SampleSentence`, `Dictionary`, `Suggestion`, `AiSession`,
  `AiSessionQuestion`, `MockTest`, `MockTestAnswer`, `UserAnswer`, `UserProgress`,
  `UserQuestionInteraction`, `UserVocabulary`, `SavedWord`, `ReviewLog`, `VideoProgress`.
  _(`ClozeSet/ClozeItem/SpeakingLine/ExerciseAttempt/SavedWord/ReviewLog/VideoProgress` are among
  the 9 schema-registry-anomaly entities — re-verify against live DB before building JPA entities.)_
- **Public API (indicative)**: `GET /topics`, `GET /topics/{id}/questions`, `POST /exercises/{id}/attempts`,
  `POST /answers`, `GET /progress/me`.
- **Sync dependencies**: Identity (profile enrichment, read-only), Media (reference `mediaId` for
  video-based exercises — read-only reference, no join).
- **Async events published**: `AnswerSubmitted` (consumed by Social for sharing), `AttemptScored`.
- **Async events consumed**: `EvaluationCompleted` (from AI Evaluation, async mode).
- **AuthN/Z**: Bearer JWT issued by Identity; verifies locally (no DB call).
- **Failure handling**: internal transaction required when one write touches `UserAnswer` +
  `UserProgress` + `UserQuestionInteraction` together — this must be explicitly designed since the
  source **does not confirm** any such transaction exists today (treat as a gap to fix, not to
  replicate).
- **Priority/complexity**: Medium priority (depends on Identity contract existing first), High
  complexity (largest entity count, SRS logic, 9-entity schema risk).

## AI Evaluation Service

- **Bounded context**: AI-driven scoring/evaluation, prompt orchestration.
- **Owned entities**: `AiPromptTemplate`, `UserPromptCache`. (`AiSession`/`AiSessionQuestion` are
  kept in Learning per source note that they are a "parallel, loosely-coupled subsystem" from
  `ielts-evaluation` — **UNKNOWN** whether they should instead move here; flagged for product
  confirmation.)
- **Public API (indicative)**: `POST /evaluations` (sync and async mode), `GET /evaluations/{id}`.
- **Sync dependencies**: none required inbound; calls out to OpenAI/Anthropic/Azure Speech.
- **Async events published**: `EvaluationCompleted`.
- **Async events consumed**: none required.
- **External integrations**: OpenAI, Anthropic, Azure Speech SDK — each needs its own timeout,
  retry-with-backoff, and per-user/day quota; tolerant JSON parsing (Java equivalent of
  `jsonrepair`, e.g. a lenient Jackson module) is required since source confirms malformed LLM
  output handling.
- **Failure handling**: must be idempotent per `evaluationId`; provider failures should not corrupt
  partial state — use a status enum (`pending/succeeded/failed`) persisted before the provider call.
- **Priority/complexity**: High priority (isolatable workload with distinct cost/latency profile),
  High complexity (multi-provider, no existing tests to use as an oracle — source confirms zero
  unit tests here).

## Media Service + Media Worker

- **Bounded context**: video ingestion, HLS transcoding, transcript, search.
- **Owned entities**: `MediaAsset`, `MediaPlay`, `MediaFavorite`, `MediaVocabSuggestion`,
  `MediaAssessment`, `Transcript`, `SearchSegment`, `DictionaryHeadword`, `DictionaryDefinition`.
  (`Transcript`, `SearchSegment` are in the 9-entity anomaly list — re-verify.)
- **Public API (indicative)**: `POST /media/ingestions`, `GET /media/{id}`, `GET /media/{id}/status`,
  `POST /media/{id}/play`, `POST /media/{id}/favorite`, `GET /media/search?q=`.
- **Media Worker**: separate deployable/process consuming `MediaIngestionRequested`; invokes
  yt-dlp + ffmpeg via `ProcessBuilder`, uploads to GCS, updates `MediaAsset` status through the
  Media Service's own DB (worker and API share the Media DB, not cross-service DB access).
- **Async events published**: `MediaIngestionCompleted`, `MediaIngestionFailed`.
- **External integrations**: GCS, Elasticsearch (search read-model, rebuildable from Postgres).
- **Failure handling**: must decompose the confirmed 2253-line `media.service.ts` god-service into
  sub-capabilities (ingestion, engagement, recommendations, assessments) as separate Spring
  `@Service` classes at minimum, per source recommendation — do not port it as one class.
  Idempotent worker retries (same `mediaId` job re-processed safely); dead-letter after N retries.
- **Priority/complexity**: Highest migration priority (operational risk: long-running jobs on a
  request/response-oriented runtime), Highest complexity.

## Social & Engagement Service

- **Bounded context**: comments, shared answers, feedback, realtime notification.
- **Owned entities**: `CommentEntity`, `SharedAnswer`, `Feedback`.
- **References (not owned)**: `answerId`, `questionId`, `userId` as opaque UUIDs.
- **Public API (indicative)**: `POST /comments`, `GET /comments?questionId=`, `POST /shared-answers`,
  `POST /feedback`.
- **Async events consumed**: `AnswerSubmitted` (to allow sharing).
- **Realtime**: Spring WebSocket/STOMP for comment fan-out — must first resolve **UNKNOWN**
  REST-vs-WebSocket single-source-of-truth question from source Flow 5 before implementing, to
  avoid duplicate or lost writes.
- **Priority/complexity**: Lower priority (few confirmed hard dependencies), Medium complexity.

## Payment Service

- **Bounded context**: VietQR payment generation, bank webhook confirmation, reconciliation.
- **Owned entities**: payment/transaction records — **UNKNOWN schema**, source `vqr` entity was not
  opened in the assessment pass; must be inspected before designing the Java entity.
- **Public API (indicative)**: `POST /payments`, `POST /payments/webhook` (bank-facing, signed).
- **Async events published**: `PaymentConfirmed`.
- **Hard requirements** (non-negotiable, security-critical): webhook signature verification,
  idempotency key / unique constraint on bank transaction reference, no distributed transaction
  with Identity — publish `PaymentConfirmed` and let Identity apply entitlement with its own retry/
  reconciliation job.
- **Priority/complexity**: Security work is urgent independent of migration sequencing; service
  _implementation_ priority is lower than Media/AI per source's own reasoning (architectural value
  of extraction is lower than Media/AI, but the security verification must not be delayed).

## API Gateway

- **Responsibility**: single entry point, JWT edge validation (signature/expiry only — not full
  authorization), routing, rate limiting, CORS, correlation ID injection, WebSocket proxying.
- **Technology candidate**: Spring Cloud Gateway — justified only if self-hosting the gateway; if
  the deployment platform offers managed routing/ingress with equivalent capabilities, prefer that
  and skip Spring Cloud Gateway (per task's own instruction not to add infra merely because it's
  common). **Decision pending deployment platform choice — UNKNOWN today.**
