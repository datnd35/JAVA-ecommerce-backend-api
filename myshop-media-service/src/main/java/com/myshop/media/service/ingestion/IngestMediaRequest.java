package com.myshop.media.service.ingestion;

import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for {@code POST /media/ingestions}. Field set is intentionally
 * minimal (Phase 1
 * scope: trigger ingestion + track status only) — see
 * {@code docs/architecture/03-service-boundaries-and-ownership.md} Media
 * Service section.
 */
public record IngestMediaRequest(@NotBlank String sourceUrl) {
}
