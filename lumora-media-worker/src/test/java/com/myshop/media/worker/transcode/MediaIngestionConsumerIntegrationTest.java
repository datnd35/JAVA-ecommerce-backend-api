package com.myshop.media.worker.transcode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.myshop.media.common.domain.MediaAsset;
import com.myshop.media.common.domain.MediaAssetRepository;
import com.myshop.media.common.domain.MediaAssetStatus;
import com.myshop.media.common.messaging.MediaIngestionRequested;
import com.myshop.media.common.messaging.MediaMessagingTopology;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Covers the Phase 1 acceptance criterion: "worker survives a simulated restart
 * mid-job without
 * losing the job (retry/idempotency test passes)" from
 * {@code docs/architecture/06-migration-roadmap.md}.
 * <p>
 * Simulates mid-job failure by making the (mocked)
 * {@link MediaTranscodeExecutor} throw on the
 * first N attempts, then succeed — equivalent to the worker process being
 * killed/restarted and
 * the message being redelivered, without needing to actually kill a JVM in the
 * test.
 */
@Testcontainers
@SpringBootTest
class MediaIngestionConsumerIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("myshop_media_worker_test")
            .withUsername("test")
            .withPassword("test");

    @Container
    static RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:3.13-management-alpine");

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.rabbitmq.host", rabbit::getHost);
        registry.add("spring.rabbitmq.port", rabbit::getAmqpPort);
        registry.add("spring.rabbitmq.username", () -> "guest");
        registry.add("spring.rabbitmq.password", () -> "guest");
    }

    @Autowired
    MediaAssetRepository mediaAssetRepository;

    @Autowired
    RabbitTemplate rabbitTemplate;

    @MockBean
    MediaTranscodeExecutor transcodeExecutor;

    @Test
    void retriesTransientFailureThenSucceeds_withoutLosingTheJob() throws Exception {
        MediaAsset asset = MediaAsset.newPending(UUID.randomUUID(), "https://youtube.com/watch?v=retry-me");
        mediaAssetRepository.save(asset);

        // First delivery fails (simulates a mid-job crash/transient error), second
        // succeeds.
        Mockito.when(transcodeExecutor.transcode(asset.getSourceUrl()))
                .thenThrow(new MediaTranscodeException("simulated transient failure"))
                .thenReturn("https://stub-gcs.invalid/manifests/recovered/index.m3u8");

        MediaIngestionRequested event = new MediaIngestionRequested(UUID.randomUUID(), asset.getId(),
                asset.getSourceUrl());
        rabbitTemplate.convertAndSend(
                MediaMessagingTopology.EXCHANGE,
                MediaMessagingTopology.INGESTION_REQUESTED_ROUTING_KEY,
                event);

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            MediaAsset reloaded = mediaAssetRepository.findById(asset.getId()).orElseThrow();
            assertThat(reloaded.getStatus()).isEqualTo(MediaAssetStatus.READY);
            assertThat(reloaded.getHlsManifestUrl())
                    .isEqualTo("https://stub-gcs.invalid/manifests/recovered/index.m3u8");
        });
    }

    @Test
    void marksFailed_afterExceedingMaxAttempts() throws Exception {
        MediaAsset asset = MediaAsset.newPending(UUID.randomUUID(), "https://youtube.com/watch?v=always-fails");
        mediaAssetRepository.save(asset);

        Mockito.when(transcodeExecutor.transcode(asset.getSourceUrl()))
                .thenThrow(new MediaTranscodeException("permanent failure"));

        MediaIngestionRequested event = new MediaIngestionRequested(UUID.randomUUID(), asset.getId(),
                asset.getSourceUrl());
        rabbitTemplate.convertAndSend(
                MediaMessagingTopology.EXCHANGE,
                MediaMessagingTopology.INGESTION_REQUESTED_ROUTING_KEY,
                event);

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            MediaAsset reloaded = mediaAssetRepository.findById(asset.getId()).orElseThrow();
            assertThat(reloaded.getStatus()).isEqualTo(MediaAssetStatus.FAILED);
            assertThat(reloaded.getAttemptCount()).isGreaterThanOrEqualTo(3);
        });
    }
}
