package com.myshop.media.worker.transcode;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

/**
 * NON-PRODUCTION placeholder implementation. Clearly labeled scaffolding per
 * the task's own rule
 * ("do not leave nonfunctional placeholder services that appear
 * production-ready"). This does NOT
 * invoke yt-dlp/ffmpeg or upload anything to GCS — it only simulates
 * success/failure so that the
 * ingestion -> worker -> status-update plumbing (Phase 1's actual acceptance
 * criterion) can be
 * proven end-to-end with Testcontainers before real native-process integration
 * is attempted.
 * <p>
 * Replace with a real {@link MediaTranscodeExecutor} implementation using
 * {@code ProcessBuilder}
 * and the GCS client library during a dedicated follow-up phase — do not extend
 * this class to
 * "sort of" do real work.
 */
@Component
public class StubMediaTranscodeExecutor implements MediaTranscodeExecutor {

    @Override
    public String transcode(String sourceUrl) throws MediaTranscodeException {
        if (sourceUrl == null || sourceUrl.isBlank()) {
            throw new MediaTranscodeException("sourceUrl is blank — cannot transcode");
        }
        // Deterministic stub manifest URL; NOT a real GCS location.
        return "https://stub-gcs.invalid/manifests/" + sourceUrl.hashCode() + "/index.m3u8";
    }
}
