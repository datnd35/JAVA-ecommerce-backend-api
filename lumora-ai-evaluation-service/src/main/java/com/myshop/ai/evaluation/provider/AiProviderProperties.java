package com.myshop.ai.evaluation.provider;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Per-provider timeout/retry/quota configuration, externalized so each provider
 * can be tuned
 * independently without code changes — per
 * {@code docs/architecture/03-service-boundaries-and-ownership.md} AI
 * Evaluation Service
 * "Failure handling" requirement ("each provider needs its own timeout,
 * retry-with-backoff, and
 * per-user/day quota").
 */
@Configuration
@ConfigurationProperties(prefix = "ai-evaluation.provider")
public class AiProviderProperties {

    /** Request timeout before treating the call as failed (retryable). */
    private Duration timeout = Duration.ofSeconds(10);

    /**
     * Max retry attempts for a single evaluation before marking it permanently
     * FAILED.
     */
    private int maxAttempts = 3;

    /**
     * Max evaluations allowed per user per day. Phase 2: declared but not yet
     * enforced — see
     * {@link com.myshop.ai.evaluation.service.EvaluationService} TODO note.
     */
    private int dailyQuotaPerUser = 100;

    public Duration getTimeout() {
        return timeout;
    }

    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public int getDailyQuotaPerUser() {
        return dailyQuotaPerUser;
    }

    public void setDailyQuotaPerUser(int dailyQuotaPerUser) {
        this.dailyQuotaPerUser = dailyQuotaPerUser;
    }
}
