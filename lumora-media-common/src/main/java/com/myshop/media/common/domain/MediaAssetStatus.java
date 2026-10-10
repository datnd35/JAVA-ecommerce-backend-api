package com.myshop.media.common.domain;

/**
 * Lifecycle status of a {@link MediaAsset}.
 * <p>
 * Modeled after the ingestion pipeline described in
 * {@code docs/architecture/02-target-microservices-architecture.md §3.6} and
 * the Open4Talk source
 * assessment's Flow 4 (YouTube ingestion -> HLS transcode). The exact source
 * enum values were not
 * opened during the read-only assessment, so this is a NEW,
 * intentionally-designed status model
 * for the Java rewrite, not a verified port of the original {@code MediaStatus}
 * enum.
 */
public enum MediaAssetStatus {
    PENDING,
    PROCESSING,
    READY,
    FAILED
}
