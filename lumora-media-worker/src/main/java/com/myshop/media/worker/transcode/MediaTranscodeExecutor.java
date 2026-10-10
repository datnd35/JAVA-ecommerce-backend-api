package com.myshop.media.worker.transcode;

/**
 * Abstraction over the actual yt-dlp + ffmpeg native process invocation
 * described in the source
 * assessment's Flow 4 (YouTube ingestion -> HLS transcode) and
 * {@code docs/architecture/02-target-microservices-architecture.md §3.6}.
 * <p>
 * <b>Phase 1 scope note:</b> only {@link StubMediaTranscodeExecutor} is
 * implemented. A real
 * implementation invoking {@code ProcessBuilder} for yt-dlp/ffmpeg and
 * uploading to GCS is
 * explicitly OUT OF SCOPE for this phase and must not be assumed
 * production-ready — see
 * {@code docs/architecture/06-migration-roadmap.md} Phase 1, which only
 * requires proving the
 * ingestion -> worker -> status-update plumbing, retry, and idempotency
 * behavior.
 */
public interface MediaTranscodeExecutor {

    /**
     * @param sourceUrl the original source URL to fetch/transcode
     * @return the resulting HLS manifest URL on success
     * @throws MediaTranscodeException if the transcode fails (triggers
     *                                 retry/dead-letter handling)
     */
    String transcode(String sourceUrl) throws MediaTranscodeException;
}
