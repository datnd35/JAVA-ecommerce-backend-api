package com.myshop.ai.evaluation.web;

import com.myshop.ai.evaluation.domain.AiProviderType;
import com.myshop.ai.evaluation.domain.Evaluation;
import com.myshop.ai.evaluation.domain.EvaluationStatus;
import com.myshop.ai.evaluation.domain.EvaluationType;
import java.util.UUID;

/**
 * Response/status DTO returned by all evaluation endpoints.
 */
public record EvaluationResponse(
        UUID id,
        EvaluationType type,
        AiProviderType provider,
        EvaluationStatus status,
        String resultJson,
        String failureReason) {
    public static EvaluationResponse from(Evaluation evaluation) {
        return new EvaluationResponse(
                evaluation.getId(),
                evaluation.getType(),
                evaluation.getProvider(),
                evaluation.getStatus(),
                evaluation.getResultJson(),
                evaluation.getFailureReason());
    }
}
