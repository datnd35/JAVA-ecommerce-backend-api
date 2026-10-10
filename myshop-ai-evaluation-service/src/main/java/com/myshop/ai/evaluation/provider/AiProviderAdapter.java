package com.myshop.ai.evaluation.provider;

import com.myshop.ai.evaluation.domain.EvaluationType;

/**
 * Abstraction over a single external AI provider call used to produce an
 * evaluation result.
 * <p>
 * Per {@code docs/architecture/05-technology-stack-decisions.md}, each provider
 * (OpenAI,
 * Anthropic, Azure Speech) gets its own adapter implementation with its own
 * timeout/retry/quota
 * policy — this is NOT a generic "one size fits all" abstraction;
 * {@link #supports(EvaluationType)}
 * lets the dispatcher pick the right adapter per request.
 * <p>
 * <b>Phase 2 scope note</b>: only {@link MockAiProviderAdapter} is implemented
 * and registered by
 * default. Real OpenAI/Anthropic/Azure Speech SDK-backed adapters are
 * explicitly OUT OF SCOPE for
 * this phase (no provider credentials are available/verified) — see roadmap
 * Phase 2 acceptance
 * criteria: "scaffold/mock provider adapter clearly labeled non-production
 * until a real provider
 * key is wired in."
 */
public interface AiProviderAdapter {

    boolean supports(EvaluationType type);

    /**
     * @param inputText the raw text/transcript to evaluate
     * @return a tolerant-JSON-parseable result string produced by the provider
     * @throws AiProviderException on timeout, quota exceeded, or any provider-side
     *                             failure
     */
    String evaluate(EvaluationType type, String inputText) throws AiProviderException;
}
