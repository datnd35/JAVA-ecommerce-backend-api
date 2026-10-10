package com.myshop.media.service.ingestion;

import com.myshop.media.common.domain.MediaAsset;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.NoSuchElementException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

/**
 * Public API surface for Media ingestion, per
 * {@code docs/architecture/03-service-boundaries-and-ownership.md} ("Media
 * Service + Media
 * Worker"). Authentication/authorization is NOT implemented yet (deferred to
 * roadmap Phase 3,
 * Identity & User Service) — this endpoint is open in Phase 1 scaffolding only
 * and MUST be secured
 * before any production exposure.
 */
@RestController
@RequestMapping("/media/ingestions")
@RequiredArgsConstructor
public class MediaIngestionController {

    private final MediaIngestionService mediaIngestionService;

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public MediaAssetStatusResponse ingest(@Valid @RequestBody IngestMediaRequest request) {
        MediaAsset asset = mediaIngestionService.requestIngestion(request.sourceUrl());
        return toResponse(asset);
    }

    @GetMapping("/{id}")
    public ResponseEntity<MediaAssetStatusResponse> getStatus(@PathVariable UUID id) {
        try {
            MediaAsset asset = mediaIngestionService.getStatus(id);
            return ResponseEntity.ok(toResponse(asset));
        } catch (NoSuchElementException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
        }
    }

    private static MediaAssetStatusResponse toResponse(MediaAsset asset) {
        return new MediaAssetStatusResponse(
                asset.getId(),
                asset.getStatus(),
                asset.getHlsManifestUrl(),
                asset.getFailureReason(),
                asset.getAttemptCount(),
                asset.getUpdatedAt());
    }
}
