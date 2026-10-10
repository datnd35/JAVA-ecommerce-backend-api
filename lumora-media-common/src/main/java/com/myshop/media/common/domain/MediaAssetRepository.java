package com.myshop.media.common.domain;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Shared by Media Service (writer on create, reader on status query) and Media
 * Worker (writer on
 * processing/ready/failed transitions). Both run against the same Postgres
 * schema owned by Media
 * — see {@code docs/architecture/04-database-and-event-strategy.md §2}.
 */
public interface MediaAssetRepository extends JpaRepository<MediaAsset, UUID> {
}
