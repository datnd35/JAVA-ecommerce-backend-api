package com.myshop.media.common.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Owned exclusively by the Media bounded context (Media Service + Media Worker
 * share this table
 * in the same "media" schema; no other service may read/write it directly — see
 * {@code docs/architecture/04-database-and-event-strategy.md §2}).
 * <p>
 * This is a deliberately MINIMAL first cut: only the columns needed to drive
 * the
 * ingest -> transcode -> ready/failed status machine (Phase 1 of the migration
 * roadmap). Fields
 * observed in the source {@code MediaAsset} entity (engagement counters,
 * recommendation metadata,
 * etc.) are NOT ported here because they were not verified in the read-only
 * assessment pass —
 * adding them now would violate the "do not invent database columns" rule.
 * Extend only after the
 * live Open4Talk schema has been diffed (roadmap Phase 0, item 2).
 */
@Entity
@Table(name = "media_asset")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MediaAsset {

    @Id
    private UUID id;

    /**
     * Source YouTube URL submitted by the client. Required, immutable after
     * creation.
     */
    @Column(name = "source_url", nullable = false, length = 2048)
    private String sourceUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private MediaAssetStatus status;

    /**
     * Populated once the worker finishes a successful transcode. Nullable until
     * then.
     */
    @Column(name = "hls_manifest_url", length = 2048)
    private String hlsManifestUrl;

    /** Populated only when status = FAILED. Nullable otherwise. */
    @Column(name = "failure_reason", length = 1024)
    private String failureReason;

    /**
     * Number of worker processing attempts made for this asset. Used for
     * retry/dead-letter logic.
     */
    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Optimistic lock: Media Service (API) and Media Worker both update this row
     * concurrently.
     */
    @Version
    @Column(name = "version", nullable = false)
    private long version;

    public static MediaAsset newPending(UUID id, String sourceUrl) {
        MediaAsset asset = new MediaAsset();
        asset.id = id;
        asset.sourceUrl = sourceUrl;
        asset.status = MediaAssetStatus.PENDING;
        asset.attemptCount = 0;
        return asset;
    }

    public void markProcessing() {
        this.status = MediaAssetStatus.PROCESSING;
        this.attemptCount += 1;
    }

    public void markReady(String hlsManifestUrl) {
        this.status = MediaAssetStatus.READY;
        this.hlsManifestUrl = hlsManifestUrl;
        this.failureReason = null;
    }

    public void markFailed(String reason) {
        this.status = MediaAssetStatus.FAILED;
        this.failureReason = reason;
    }
}
