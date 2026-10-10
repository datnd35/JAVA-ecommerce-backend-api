package com.myshop.media.service.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.myshop.media.common.domain.MediaAssetRepository;
import com.myshop.media.common.domain.MediaAssetStatus;
import com.myshop.media.common.messaging.MediaMessagingTopology;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Integration test for the ingestion -> queue path described in
 * {@code docs/architecture/06-migration-roadmap.md} Phase 1 acceptance
 * criteria: "Add
 * Testcontainers-based integration tests for the ingestion -> worker ->
 * status-update path."
 * <p>
 * This test covers the Media Service side of that path (HTTP request -> DB row
 * ->
 * message published). The worker-side consumption and status update is covered
 * separately in
 * {@code myshop-media-worker}'s own Testcontainers test, since the two are
 * independently
 * deployable processes per the architecture decision — there is no single
 * process to test both
 * halves together without duplicating one of the two Spring contexts.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MediaIngestionIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("myshop_media_test")
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

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    MediaAssetRepository mediaAssetRepository;

    @Autowired
    RabbitTemplate rabbitTemplate;

    @Test
    void ingestRequest_createsPendingAssetAndPublishesEvent() {
        IngestMediaRequest request = new IngestMediaRequest("https://youtube.com/watch?v=abc123");

        ResponseEntity<MediaAssetStatusResponse> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/media/ingestions", request, MediaAssetStatusResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        UUID mediaAssetId = response.getBody().id();
        assertThat(response.getBody().status()).isEqualTo(MediaAssetStatus.PENDING);

        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(mediaAssetRepository.findById(mediaAssetId)).isPresent());

        Message queuedMessage = rabbitTemplate.receive(
                MediaMessagingTopology.INGESTION_REQUESTED_QUEUE, 5000);
        assertThat(queuedMessage).isNotNull();
    }

    @Test
    void getStatus_returnsNotFound_forUnknownId() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/media/ingestions/" + UUID.randomUUID(), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
