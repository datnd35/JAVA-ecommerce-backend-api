package com.myshop.media.common.messaging;

import java.io.Serializable;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Event published by Media Service when a new ingestion job is accepted,
 * consumed by Media
 * Worker. Mirrors the "ingest -> queue -> worker" flow described in
 * {@code docs/architecture/02-target-microservices-architecture.md §3.6}.
 * <p>
 * {@code messageId} is the idempotency key the worker uses to de-duplicate
 * redelivered messages
 * (at-least-once broker semantics) — see
 * {@code docs/architecture/04-database-and-event-strategy.md §5}.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class MediaIngestionRequested implements Serializable {

    private UUID messageId;
    private UUID mediaAssetId;
    private String sourceUrl;
}
