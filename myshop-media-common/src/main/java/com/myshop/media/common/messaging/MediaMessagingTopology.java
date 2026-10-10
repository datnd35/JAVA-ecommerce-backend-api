package com.myshop.media.common.messaging;

/**
 * Central place for RabbitMQ topology names shared by producer (Media Service)
 * and consumer
 * (Media Worker). Keeping this in the shared module avoids magic-string drift
 * between the two
 * deployables while NOT sharing business entities/repositories across service
 * boundaries.
 */
public final class MediaMessagingTopology {

    public static final String EXCHANGE = "media.exchange";

    public static final String INGESTION_REQUESTED_ROUTING_KEY = "media.ingestion.requested";
    public static final String INGESTION_REQUESTED_QUEUE = "media.ingestion.requested.q";

    /**
     * Dead-letter queue for ingestion jobs that exhausted retries. Bound to the
     * same exchange via
     * a separate routing key so the worker can nack-without-requeue into it.
     */
    public static final String INGESTION_DEAD_LETTER_ROUTING_KEY = "media.ingestion.requested.dlq";
    public static final String INGESTION_DEAD_LETTER_QUEUE = "media.ingestion.requested.dlq";

    private MediaMessagingTopology() {
    }
}
