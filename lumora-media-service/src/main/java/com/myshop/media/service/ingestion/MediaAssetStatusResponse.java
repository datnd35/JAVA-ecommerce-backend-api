package com.myshop.media.service.ingestion;

import com.myshop.media.common.domain.MediaAssetStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for both the initial 202-Accepted response and the
 * status-polling endpoint.
 */
public record MediaAssetStatusResponse(
        UUID id,
        MediaAssetStatus status,
        String hlsManifestUrl,
        String failureReason,
        int attemptCount,
        Instant updatedAt) {
}
