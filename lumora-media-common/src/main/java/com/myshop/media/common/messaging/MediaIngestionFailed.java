package com.myshop.media.common.messaging;

import java.io.Serializable;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Published by Media Worker when a transcode job permanently fails (retries
 * exhausted). See
 * {@code docs/architecture/04-database-and-event-strategy.md §6} for the
 * retry/dead-letter policy
 * this event is part of.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class MediaIngestionFailed implements Serializable {

    private UUID messageId;
    private UUID mediaAssetId;
    private String reason;
}
