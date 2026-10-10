package com.myshop.media.worker.transcode;

/**
 * Thrown when a transcode attempt fails. Caught by
 * {@link MediaIngestionConsumer} to drive the
 * retry/dead-letter policy from
 * {@code docs/architecture/04-database-and-event-strategy.md §6}.
 */
public class MediaTranscodeException extends Exception {

    public MediaTranscodeException(String message) {
        super(message);
    }

    public MediaTranscodeException(String message, Throwable cause) {
        super(message, cause);
    }
}
