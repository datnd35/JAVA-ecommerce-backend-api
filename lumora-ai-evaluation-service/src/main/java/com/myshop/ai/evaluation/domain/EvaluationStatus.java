package com.myshop.ai.evaluation.domain;

/**
 * Lifecycle of a single evaluation request. Modeled explicitly as a persisted
 * status machine
 * (not inferred from presence/absence of a result column) so that:
 * <ul>
 * <li>a provider failure can be recorded without losing the original request
 * (needed for the
 * async mode's "client polls status" flow, per
 * {@code docs/architecture/02-target-microservices-architecture.md §3.5});</li>
 * <li>idempotent retries are possible (re-submitting a scoring attempt for the
 * same
 * {@code evaluationId} is safe regardless of current status).</li>
 * </ul>
 * This is a NEW status model designed for the Java rewrite — the source NestJS
 * {@code ielts-evaluation} module's exact response/state handling was not
 * opened in the read-only
 * assessment pass (source {@code 05-runtime-flows.md} Flow 3 marks several
 * steps "Inferred" /
 * "Unknown exact schema"), so this must not be treated as a verified port of
 * legacy behavior.
 */
public enum EvaluationStatus {
    PENDING,
    IN_PROGRESS,
    SUCCEEDED,
    FAILED
}
