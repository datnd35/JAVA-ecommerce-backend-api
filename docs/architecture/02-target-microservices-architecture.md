# 02 — Target Microservices Architecture

This adapts and validates the candidate design already proposed in the source assessment
(`docs/architecture-assessment/08-microservices-architecture-design.md`, read as prior art) against
the target project's real stack (see `01-current-state-assessment.md`). Candidate services are
**not mandatory deployment units** — consolidate/split as evidence dictates.

## 1. Candidate services

| Service                  | Source modules absorbed                                                                                                                                                                                                                                | Responsibility                                                                                                                                |
| ------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | --------------------------------------------------------------------------------------------------------------------------------------------- |
| **Identity & User**      | `auth`, `users`                                                                                                                                                                                                                                        | AuthN (Google OAuth, optionally Clerk), JWT issuance, profile, subscription/entitlement flags, authorization policy                           |
| **Learning**             | `topics`, `questions`, `exercises`, `vocabulary`, `sample-sentence`, `dictionary`, `suggestions`, `ai-sessions`, `ai-session-question`, `mock-test`, `user-answers`, `user-progress`, `user-question-interaction`, `user-vocabulary`, `learning` (SRS) | Curriculum content, practice sessions, answers, progress, spaced repetition                                                                   |
| **AI Evaluation**        | `ielts-evaluation`, `speaking-analysis`, `ai-prompt-templates`, `user-prompt-cache`                                                                                                                                                                    | Grammar/vocab/coherence/pronunciation scoring, multi-provider orchestration (OpenAI/Anthropic/Azure Speech), prompt templates                 |
| **Media + Media Worker** | `media`, `transcript`, `search`                                                                                                                                                                                                                        | YouTube ingestion, FFmpeg/yt-dlp HLS transcoding (worker), engagement tracking, transcript, Elasticsearch-backed search                       |
| **Social & Engagement**  | `comment`, `shared-answers`, `feedbacks`, notification part of `web_socket`                                                                                                                                                                            | Comments, shared answers, feedback, realtime notification                                                                                     |
| **Payment**              | `vqr`                                                                                                                                                                                                                                                  | QR payment generation, bank webhook confirmation, reconciliation — **security-critical, verify webhook signature validation before any port** |
| **API Gateway**          | n/a (new)                                                                                                                                                                                                                                              | Single entry point, JWT edge validation, routing, rate limiting, WebSocket passthrough                                                        |
| Platform/cross-cutting   | `events`, remaining `web_socket`, `share`, `posthog`, `health`                                                                                                                                                                                         | Not necessarily a deployable service; may remain library code or a small ops service                                                          |

## 2. Why these boundaries

- **Identity** is split out because `UserService` is a shared kernel today (confirmed dependency
  from nearly every module) — isolating it first, behind an API/contract, is the precondition for
  every other service to stop doing direct DB joins against `User`.
- **Media** is isolated primarily for _runtime characteristics_, not just business capability: it
  is the only workload with long-running native-process execution (ffmpeg/yt-dlp) that does not fit
  a request/response Cloud Run-style lifecycle — confirmed risk in source `07-technical-debt.md`
  ("Deployment & Operational Risk Notes"). The **Media Worker** must be independently deployable and
  scalable from the Media API for this reason alone.
- **AI Evaluation** is isolated because of distinct non-functional needs: external provider
  timeouts/cost/quota control, tolerant JSON parsing, and retry policies that must not block or be
  coupled to Learning CRUD latency.
- **Learning** stays a single, relatively large bounded context (not split per CRUD module) because
  entities like `Question`, `Exercise`, `UserAnswer`, `MockTest`, `UserProgress` are transactionally
  related within one user-facing workflow (submitting an answer updates progress); splitting further
  would force distributed transactions for no proven scaling benefit.
- **Social** stays decoupled from Learning: it references `answerId`/`questionId` by ID, not by
  owning the record, avoiding a shared-write table.
- **Payment** is isolated for blast-radius and compliance reasons (financial data, webhook trust
  boundary) even though it has no confirmed dependency on any learning entity today.
- **API Gateway** is justified because there are two real client types (web + mobile) that need one
  stable entry point, JWT edge-checks, and WebSocket routing — not because "microservices need a
  gateway" by default.

## 3. Diagrams

### 3.1 System context / container view

```mermaid
flowchart TB
    web[Web App] --> gw[API Gateway]
    mobile[Mobile App] --> gw
    gw --> identity[Identity & User Service]
    gw --> learning[Learning Service]
    gw --> ai[AI Evaluation Service]
    gw --> media[Media Service]
    gw --> social[Social & Engagement Service]
    gw --> payment[Payment Service]
    media --> worker[Media Worker]
    worker --> gcs[(GCS)]
    media --> es[(Elasticsearch)]
    ai --> openai[(OpenAI/Anthropic/Azure Speech)]
    payment --> vietqr[(VietQR/Bank Webhook)]
    identity --> idpgoog[(Google OAuth / Clerk)]
```

### 3.2 Service boundaries and dependencies

```mermaid
flowchart LR
    identity -->|user profile API, JWT verification| learning
    identity -->|user profile API| ai
    identity -->|user profile API| media
    identity -->|user profile API| social
    identity -->|entitlement events| payment
    learning -->|answerId reference, event: AnswerSubmitted| social
    learning -->|questionContext| ai
    media -->|mediaId reference| learning
    payment -->|PaymentConfirmed event| identity
```

### 3.3 Database ownership

```mermaid
flowchart TB
    subgraph Identity DB
        User
        Friend
    end
    subgraph Learning DB
        Topic
        Question
        Exercise
        UserAnswer
        UserProgress
        MockTest
    end
    subgraph AI DB
        AiSession
        AiPromptTemplate
        UserPromptCache
    end
    subgraph Media DB
        MediaAsset
        Transcript
    end
    subgraph Social DB
        Comment
        SharedAnswer
        Feedback
    end
    subgraph Payment DB
        PaymentTransaction
    end
```

All on one PostgreSQL cluster initially (separate schemas/users per service), not separate physical
servers — see `04-database-and-event-strategy.md`.

### 3.4 Authentication flow

```mermaid
sequenceDiagram
    Client->>Gateway: POST /auth/google {code}
    Gateway->>Identity: forward
    Identity->>Google: exchange code
    Identity->>Identity: create/update User, issue JWT
    Identity-->>Client: accessToken + profile
    Client->>Gateway: request + Bearer JWT
    Gateway->>Gateway: verify signature/expiry (edge check)
    Gateway->>TargetService: forward + claims
    TargetService->>TargetService: enforce authorization (not just "passed gateway")
```

### 3.5 AI evaluation flow (sync and async modes)

```mermaid
sequenceDiagram
    Client->>AIEvaluation: POST /evaluations (sync) OR
    Client->>AIEvaluation: POST /evaluations (async, returns evaluationId)
    AIEvaluation->>Provider: OpenAI/Anthropic/Azure Speech call
    Provider-->>AIEvaluation: result (tolerant JSON parse)
    AIEvaluation->>AIEvaluation: persist result / cache
    AIEvaluation-->>Client: result (sync) or EvaluationCompleted event / poll (async)
```

### 3.6 Media ingestion and HLS processing

```mermaid
sequenceDiagram
    Client->>Media: POST /media/ingestions {youtubeUrl}
    Media->>Media: create MediaAsset (status=pending)
    Media->>Queue: publish MediaIngestionRequested
    Media-->>Client: 202 Accepted {mediaId}
    Queue->>MediaWorker: consume
    MediaWorker->>MediaWorker: yt-dlp fetch + ffmpeg HLS transcode
    MediaWorker->>GCS: upload segments/manifest
    MediaWorker->>Media: update MediaAsset status (DB owned by Media)
    MediaWorker->>Queue: publish MediaIngestionCompleted
    Media-->>Client: WebSocket/poll status update
```

### 3.7 Payment confirmation and subscription update

```mermaid
sequenceDiagram
    Bank->>Payment: webhook (signed)
    Payment->>Payment: verify signature + idempotency key
    Payment->>Payment: persist transaction (unique constraint on bank ref)
    Payment->>Queue: publish PaymentConfirmed
    Queue->>Identity: consume (idempotent)
    Identity->>Identity: update entitlement/subscription
    Identity->>Queue: publish EntitlementUpdated (optional, for client notification)
```

### 3.8 Synchronous vs. asynchronous communication

```mermaid
flowchart LR
    subgraph Sync HTTP
        A[Gateway to Identity: verify token/user lookup]
        B[Learning to Identity: fetch profile for response enrichment]
        C[Client to AI Evaluation: short sync scoring]
    end
    subgraph Async events
        D[Media to Learning: MediaIngestionCompleted]
        E[Payment to Identity: PaymentConfirmed]
        F[Learning to Social: AnswerSubmitted]
        G[AI Evaluation to Learning: EvaluationCompleted, async mode]
    end
```

## 4. Open questions carried into `03-service-boundaries-and-ownership.md`

- Whether Clerk is retained or consolidated into a single Spring Security/OAuth2 resource-server
  flow — **UNKNOWN**, product decision required.
- Whether comments are written via REST, WebSocket, or both in the source — **UNKNOWN**
  (source `05-runtime-flows.md` Flow 5), must be resolved before designing the Social service write
  path, to avoid silently dropping or duplicating functionality.
