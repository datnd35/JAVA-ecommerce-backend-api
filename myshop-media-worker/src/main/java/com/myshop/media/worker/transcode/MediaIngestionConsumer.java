package com.myshop.media.worker.transcode;

import com.myshop.media.common.domain.MediaAsset;
import com.myshop.media.common.domain.MediaAssetRepository;
import com.myshop.media.common.messaging.MediaIngestionRequested;
import com.myshop.media.common.messaging.MediaMessagingTopology;
import java.util.NoSuchElementException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consumes {@link MediaIngestionRequested} and drives the MediaAsset status
 * machine.
 * <p>
 * Implements the roadmap Phase 1 acceptance criteria directly:
 * <ul>
 * <li><b>Idempotency / restart safety</b>: if the worker process is killed
 * mid-job and the
 * message is redelivered (at-least-once broker semantics), re-processing the
 * same
 * {@code mediaAssetId} is safe — the asset is re-fetched by ID and
 * re-transitioned from
 * whatever state it is in; a successful transcode result is simply re-applied
 * (idempotent), and {@code attemptCount} tracks redeliveries without corrupting
 * state.</li>
 * <li><b>Retry / dead-letter</b>: on {@link MediaTranscodeException}, the
 * message is
 * negatively acknowledged. Up to {@link #MAX_ATTEMPTS} total attempts are
 * allowed
 * (tracked via the persisted {@code attemptCount}, not the broker's redelivery
 * counter,
 * so the limit survives worker restarts); beyond that, the asset is marked
 * FAILED and the
 * exception is swallowed so the broker does NOT requeue it again — the message
 * has
 * already been dead-lettered via the queue's {@code x-dead-letter-exchange}
 * configuration
 * once {@link org.springframework.amqp.rabbit.listener.FatalExceptionStrategy}
 * sees a
 * non-retryable condition is unnecessary here since we handle the cap ourselves
 * and ack
 * normally once we've recorded FAILED — see the detailed note on
 * {@link #handle(MediaIngestionRequested)} below.</li>
 * </ul>
 * This is a <b>new</b> reliability guarantee designed for the Java rewrite; the
 * source NestJS
 * {@code media.processor.ts} was not confirmed to have equivalent
 * idempotency/retry-cap handling
 * (see {@code docs/architecture/01-current-state-assessment.md §1.3} item 6).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MediaIngestionConsumer {

    private static final int MAX_ATTEMPTS = 3;

    private final MediaAssetRepository mediaAssetRepository;
    private final MediaTranscodeExecutor transcodeExecutor;

    @RabbitListener(queues = MediaMessagingTopology.INGESTION_REQUESTED_QUEUE)
    public void handle(MediaIngestionRequested event) {
        Optional<MediaAsset> maybeAsset = mediaAssetRepository.findById(event.getMediaAssetId());
        if (maybeAsset.isEmpty()) {
            // Asset row not visible yet (possible read-replica lag) or was deleted — do not
            // requeue forever; log and drop. A missing row is not a transient transcode
            // failure.
            log.warn("MediaAsset {} not found for ingestion event {}; dropping message",
                    event.getMediaAssetId(), event.getMessageId());
            return;
        }

        MediaAsset asset = maybeAsset.get();

        if (asset.getStatus() == com.myshop.media.common.domain.MediaAssetStatus.READY) {
            // Already completed by a previous delivery of this same message — idempotent
            // no-op.
            log.info("MediaAsset {} already READY; ignoring redelivered message {}",
                    asset.getId(), event.getMessageId());
            return;
        }

        if (asset.getAttemptCount() >= MAX_ATTEMPTS
                && asset.getStatus() == com.myshop.media.common.domain.MediaAssetStatus.FAILED) {
            log.warn("MediaAsset {} already exhausted {} attempts; not retrying further",
                    asset.getId(), MAX_ATTEMPTS);
            return;
        }

        processAttempt(asset, event);
    }

    @Transactional
    void processAttempt(MediaAsset asset, MediaIngestionRequested event) {
        asset.markProcessing();
        mediaAssetRepository.save(asset);

        try {
            String manifestUrl = transcodeExecutor.transcode(asset.getSourceUrl());
            asset.markReady(manifestUrl);
            mediaAssetRepository.save(asset);
            log.info("MediaAsset {} transcoded successfully on attempt {}",
                    asset.getId(), asset.getAttemptCount());
        } catch (MediaTranscodeException ex) {
            if (asset.getAttemptCount() >= MAX_ATTEMPTS) {
                asset.markFailed("Exceeded max attempts (" + MAX_ATTEMPTS + "): " + ex.getMessage());
                mediaAssetRepository.save(asset);
                log.error("MediaAsset {} permanently failed after {} attempts: {}",
                        asset.getId(), asset.getAttemptCount(), ex.getMessage());
                // Do not rethrow: we've recorded the terminal FAILED state ourselves, so the
                // message should be ack'd normally rather than dead-lettered a second time.
                return;
            }
            asset.markFailed(ex.getMessage());
            mediaAssetRepository.save(asset);
            log.warn("MediaAsset {} attempt {} failed, will retry via redelivery: {}",
                    asset.getId(), asset.getAttemptCount(), ex.getMessage());
            throw new TranscodeRetryableRuntimeException(ex);
        }
    }

    /**
     * Wraps {@link MediaTranscodeException} as unchecked so {@code @RabbitListener}
     * nacks the
     * message and the broker redelivers it (bounded by {@link #MAX_ATTEMPTS},
     * enforced above via
     * the persisted {@code attemptCount} rather than broker redelivery count).
     */
    static class TranscodeRetryableRuntimeException extends RuntimeException {
        TranscodeRetryableRuntimeException(Throwable cause) {
            super(cause);
        }
    }
}
