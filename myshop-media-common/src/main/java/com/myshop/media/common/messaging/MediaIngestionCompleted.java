package com.myshop.media.common.messaging;

import java.io.Serializable;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Published by Media Worker when a transcode finishes successfully. Future
 * consumers (e.g.
 * Learning Service, per {@code docs/architecture/02 §3.2}) react to this to
 * unlock video-based
 * exercises — no consumer is implemented yet in Phase 1, this is the contract
 * only.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class MediaIngestionCompleted implements Serializable {

    private UUID messageId;
    private UUID mediaAssetId;
    private String hlsManifestUrl;
}
