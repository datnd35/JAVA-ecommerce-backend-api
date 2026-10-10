package com.myshop.media.service.ingestion;

import com.myshop.media.common.domain.MediaAsset;
import com.myshop.media.common.domain.MediaAssetRepository;
import com.myshop.media.common.messaging.MediaIngestionRequested;
import com.myshop.media.common.messaging.MediaMessagingTopology;
import java.util.NoSuchElementException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns the ingestion sub-capability only: accept a new ingestion request,
 * persist the pending
 * {@link MediaAsset}, and publish {@link MediaIngestionRequested} for the
 * worker to pick up.
 * <p>
 * Deliberately NOT combined with engagement/recommendation/assessment logic —
 * see
 * {@code docs/architecture/06-migration-roadmap.md} Phase 1 ("do not create one
 * god-service"),
 * which is the direct corrective action for the source's confirmed 2253-line
 * {@code media.service.ts} god-service (source {@code 07-technical-debt.md}
 * issue #8). Those other
 * sub-capabilities are out of scope for Phase 1 and must be added as separate
 * {@code @Service}
 * classes (e.g. {@code MediaEngagementService}) when implemented, never merged
 * into this one.
 */
@Service
@RequiredArgsConstructor
public class MediaIngestionService {

    private final MediaAssetRepository mediaAssetRepository;
    private final RabbitTemplate rabbitTemplate;

    @Transactional
    public MediaAsset requestIngestion(String sourceUrl) {
        MediaAsset asset = MediaAsset.newPending(UUID.randomUUID(), sourceUrl);
        mediaAssetRepository.save(asset);

        MediaIngestionRequested event = new MediaIngestionRequested(UUID.randomUUID(), asset.getId(), sourceUrl);
        rabbitTemplate.convertAndSend(
                MediaMessagingTopology.EXCHANGE,
                MediaMessagingTopology.INGESTION_REQUESTED_ROUTING_KEY,
                event);

        return asset;
    }

    @Transactional(readOnly = true)
    public MediaAsset getStatus(UUID mediaAssetId) {
        return mediaAssetRepository.findById(mediaAssetId)
                .orElseThrow(() -> new NoSuchElementException("MediaAsset not found: " + mediaAssetId));
    }
}
