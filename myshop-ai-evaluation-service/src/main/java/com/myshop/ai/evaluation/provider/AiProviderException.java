package com.myshop.ai.evaluation.provider;

/**
 * Thrown by an {@link AiProviderAdapter} on timeout, quota-exceeded, or any
 * other provider-side
 * failure. Distinguishes retryable vs. non-retryable conditions so the caller
 * (e.g.
 * {@code EvaluationService}) can decide whether to retry or fail permanently
 * without needing to
 * inspect provider-specific exception types.
 */
public class AiProviderException extends Exception {

    private final boolean retryable;

    public AiProviderException(String message, boolean retryable) {
        super(message);
        this.retryable = retryable;
    }

    public AiProviderException(String message, Throwable cause, boolean retryable) {
        super(message, cause);
        this.retryable = retryable;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
