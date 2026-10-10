package com.myshop.media.common.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declares the RabbitMQ topology (exchange, main queue, dead-letter queue) used
 * by both Media
 * Service (producer) and Media Worker (consumer). Declared idempotently by both
 * apps on startup
 * so either one can be started first in local/dev environments.
 * <p>
 * Dead-lettering: the main queue's messages, once nacked without requeue (after
 * retries exhausted
 * in the worker), are routed to the DLQ via {@code x-dead-letter-exchange} —
 * this implements the
 * "retry, dead-letter" requirement from
 * {@code docs/architecture/04-database-and-event-strategy.md §6}.
 */
@Configuration
public class MediaRabbitConfig {

    @Bean
    public DirectExchange mediaExchange() {
        return new DirectExchange(MediaMessagingTopology.EXCHANGE, true, false);
    }

    @Bean
    public Queue mediaIngestionRequestedQueue() {
        return QueueBuilder.durable(MediaMessagingTopology.INGESTION_REQUESTED_QUEUE)
                .withArgument("x-dead-letter-exchange", MediaMessagingTopology.EXCHANGE)
                .withArgument("x-dead-letter-routing-key", MediaMessagingTopology.INGESTION_DEAD_LETTER_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue mediaIngestionDeadLetterQueue() {
        return QueueBuilder.durable(MediaMessagingTopology.INGESTION_DEAD_LETTER_QUEUE).build();
    }

    @Bean
    public Binding mediaIngestionRequestedBinding(Queue mediaIngestionRequestedQueue, DirectExchange mediaExchange) {
        return BindingBuilder.bind(mediaIngestionRequestedQueue)
                .to(mediaExchange)
                .with(MediaMessagingTopology.INGESTION_REQUESTED_ROUTING_KEY);
    }

    @Bean
    public Binding mediaIngestionDeadLetterBinding(Queue mediaIngestionDeadLetterQueue, DirectExchange mediaExchange) {
        return BindingBuilder.bind(mediaIngestionDeadLetterQueue)
                .to(mediaExchange)
                .with(MediaMessagingTopology.INGESTION_DEAD_LETTER_ROUTING_KEY);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
