package com.myshop.ai.evaluation.web;

import com.myshop.ai.evaluation.domain.AiProviderType;
import com.myshop.ai.evaluation.domain.EvaluationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request body for both {@code POST /evaluations} (sync) and
 * {@code POST /evaluations/async}.
 */
public record EvaluateRequest(
        @NotNull EvaluationType type,
        @NotNull AiProviderType provider,
        @NotBlank String inputText) {
}
