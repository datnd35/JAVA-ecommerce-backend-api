package com.myshop.ai.evaluation.service;

import com.myshop.ai.evaluation.domain.AiProviderType;
import com.myshop.ai.evaluation.domain.Evaluation;
import com.myshop.ai.evaluation.domain.EvaluationRepository;
import com.myshop.ai.evaluation.domain.EvaluationType;
import com.myshop.ai.evaluation.provider.AiProviderAdapter;
import com.myshop.ai.evaluation.provider.AiProviderException;
import com.myshop.ai.evaluation.provider.AiProviderProperties;
import java.util.NoSuchElementException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates a single evaluation request against the dispatched
 * {@link AiProviderAdapter},
 * supporting both the sync and async modes described in
 * {@code docs/architecture/02-target-microservices-architecture.md §3.5}.
 * <p>
 * Idempotency/retry design (per
 * {@code docs/architecture/03-service-boundaries-and-ownership.md} "Failure
 * handling"):
 * the {@link Evaluation} row is persisted as PENDING before any provider call,
 * so a crash
 * mid-call never loses the request; retries are bounded by
 * {@link AiProviderProperties#getMaxAttempts()} and only occur for
 * {@link AiProviderException#isRetryable()} failures — non-retryable failures
 * (and attempts
 * exhausted) terminate as FAILED immediately.
 * <p>
 * <b>Quota enforcement is NOT implemented in Phase 2</b>
 * ({@link AiProviderProperties#getDailyQuotaPerUser()}
 * is declared but unused) — this must not be assumed as an existing safeguard;
 * add it before any
 * production exposure that accepts untrusted traffic.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EvaluationService {

    private final EvaluationRepository evaluationRepository;
    private final AiProviderDispatcher dispatcher;
    private final AiProviderProperties providerProperties;

    /**
     * Synchronous mode: blocks until the provider responds or retries are
     * exhausted. Intended for
     * short, latency-predictable evaluation types per the architecture doc's "Mode
     * A — Synchronous".
     */
    @Transactional
    public Evaluation evaluateSync(EvaluationType type, AiProviderType provider, String inputText) {
        Evaluation evaluation = Evaluation.newPending(UUID.randomUUID(), type, provider, inputText);
        evaluationRepository.save(evaluation);
        runEvaluation(evaluation);
        return evaluation;
    }

    /**
     * Asynchronous mode: persists the PENDING evaluation and returns immediately;
     * processing
     * happens on a separate thread (see
     * {@link com.myshop.ai.evaluation.config.AsyncConfig}).
     * Caller polls {@link #getEvaluation(UUID)} for completion, per the
     * architecture doc's
     * "Mode B — Asynchronous" flow.
     */
    @Transactional
    public Evaluation submitAsync(EvaluationType type, AiProviderType provider, String inputText) {
        Evaluation evaluation = Evaluation.newPending(UUID.randomUUID(), type, provider, inputText);
        evaluationRepository.save(evaluation);
        processAsync(evaluation.getId());
        return evaluation;
    }

    @Async
    public void processAsync(UUID evaluationId) {
        Evaluation evaluation = evaluationRepository.findById(evaluationId)
                .orElseThrow(() -> new NoSuchElementException("Evaluation not found: " + evaluationId));
        runEvaluation(evaluation);
    }

    @Transactional(readOnly = true)
    public Evaluation getEvaluation(UUID id) {
        return evaluationRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Evaluation not found: " + id));
    }

    /**
     * Shared retry loop used by both sync and async modes. Bounded by
     * {@link AiProviderProperties#getMaxAttempts()}; only
     * {@link AiProviderException#isRetryable()}
     * failures are retried — a non-retryable failure terminates immediately
     * regardless of attempt
     * count.
     */
    @Transactional
    void runEvaluation(Evaluation evaluation) {
        evaluation.markInProgress();
        evaluationRepository.save(evaluation);

        AiProviderAdapter adapter = dispatcher.forType(evaluation.getType());
        int maxAttempts = providerProperties.getMaxAttempts();

        AiProviderException lastFailure = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                String resultJson = adapter.evaluate(evaluation.getType(), evaluation.getInputText());
                evaluation.markSucceeded(resultJson);
                evaluationRepository.save(evaluation);
                log.info("Evaluation {} succeeded on attempt {}/{}", evaluation.getId(), attempt, maxAttempts);
                return;
            } catch (AiProviderException ex) {
                lastFailure = ex;
                if (!ex.isRetryable()) {
                    log.warn("Evaluation {} failed non-retryably: {}", evaluation.getId(), ex.getMessage());
                    break;
                }
                log.warn("Evaluation {} attempt {}/{} failed (retryable): {}",
                        evaluation.getId(), attempt, maxAttempts, ex.getMessage());
            }
        }

        evaluation.markFailed(lastFailure != null ? lastFailure.getMessage() : "Unknown failure");
        evaluationRepository.save(evaluation);
    }
}
