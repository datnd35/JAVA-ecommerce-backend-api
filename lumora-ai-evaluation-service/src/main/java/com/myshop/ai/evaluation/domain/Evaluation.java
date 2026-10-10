package com.myshop.ai.evaluation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Owned exclusively by the AI Evaluation bounded context — see
 * {@code docs/architecture/03-service-boundaries-and-ownership.md} ("AI
 * Evaluation Service").
 * <p>
 * Persisted BEFORE the provider call is made (status=PENDING), per
 * {@code docs/architecture/03-service-boundaries-and-ownership.md} "Failure
 * handling" note:
 * "must be idempotent per evaluationId; provider failures should not corrupt
 * partial state — use
 * a status enum persisted before the provider call." This guarantees that even
 * if the JVM crashes
 * mid-call, the evaluation row exists and can be retried/inspected, rather than
 * being lost.
 */
@Entity
@Table(name = "evaluation")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Evaluation {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 32)
    private EvaluationType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 32)
    private AiProviderType provider;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private EvaluationStatus status;

    /** Raw text/transcript submitted for evaluation. */
    @Lob
    @Column(name = "input_text", nullable = false)
    private String inputText;

    /**
     * Tolerant-JSON-parsed provider result, stored as-is once available. Nullable
     * until SUCCEEDED.
     */
    @Lob
    @Column(name = "result_json")
    private String resultJson;

    /** Populated only when status = FAILED. */
    @Column(name = "failure_reason", length = 2048)
    private String failureReason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    public static Evaluation newPending(UUID id, EvaluationType type, AiProviderType provider, String inputText) {
        Evaluation evaluation = new Evaluation();
        evaluation.id = id;
        evaluation.type = type;
        evaluation.provider = provider;
        evaluation.inputText = inputText;
        evaluation.status = EvaluationStatus.PENDING;
        return evaluation;
    }

    public void markInProgress() {
        this.status = EvaluationStatus.IN_PROGRESS;
    }

    public void markSucceeded(String resultJson) {
        this.status = EvaluationStatus.SUCCEEDED;
        this.resultJson = resultJson;
        this.failureReason = null;
    }

    public void markFailed(String reason) {
        this.status = EvaluationStatus.FAILED;
        this.failureReason = reason;
    }
}
