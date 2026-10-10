package com.myshop.ai.evaluation.domain;

/**
 * Which external AI provider produced (or is producing) an evaluation result.
 * Per
 * {@code docs/architecture/05-technology-stack-decisions.md} — direct SDK/HTTP
 * clients for each
 * of these, not a generic abstraction, since provider-specific features (e.g.
 * Azure Speech's
 * phoneme-level pronunciation scoring) may not be uniformly expressible.
 */
public enum AiProviderType {
    OPENAI,
    ANTHROPIC,
    AZURE_SPEECH
}
