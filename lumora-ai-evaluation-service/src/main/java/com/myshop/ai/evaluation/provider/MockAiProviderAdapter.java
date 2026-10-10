package com.myshop.ai.evaluation.provider;

import com.myshop.ai.evaluation.domain.EvaluationType;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

/**
 * NON-PRODUCTION placeholder adapter. Per roadmap Phase 2 acceptance criteria:
 * "a scaffold/mock
 * provider adapter clearly labeled non-production until a real provider key is
 * wired in — do not
 * ship a fake-looking production integration."
 * <p>
 * This does NOT call OpenAI/Anthropic/Azure Speech. It deterministically
 * simulates a tolerant-JSON
 * result (or a failure, for specific inputs used in tests) so the sync/async
 * evaluation plumbing,
 * timeout/retry handling, and REST contract can be proven end-to-end before
 * real provider
 * credentials and SDKs are integrated in a dedicated follow-up phase.
 * <p>
 * Replace with real {@code OpenAiProviderAdapter} /
 * {@code AnthropicProviderAdapter} /
 * {@code AzureSpeechProviderAdapter} implementations — do not extend this class
 * to "sort of" call
 * a real provider.
 */
@Component
public class MockAiProviderAdapter implements AiProviderAdapter {

    private static final Set<EvaluationType> SUPPORTED = Set.of(
            EvaluationType.GRAMMAR, EvaluationType.PRONUNCIATION);

    /**
     * Magic input prefix tests use to deterministically simulate a provider
     * failure. If the input is exactly this constant, every attempt fails
     * (used to prove "retries exhausted -> FAILED"). If the input starts with
     * this constant followed by {@code "-once"}, only the FIRST call for that
     * exact input fails and subsequent calls succeed (used to prove
     * "retry then succeed").
     */
    public static final String SIMULATED_FAILURE_TRIGGER = "__SIMULATE_PROVIDER_FAILURE__";

    private static final String ONCE_SUFFIX = "-once";

    /**
     * Tracks remaining forced failures per distinct "-once" input, for the
     * retry-then-succeed case.
     */
    private final ConcurrentHashMap<String, AtomicInteger> onceFailureCounters = new ConcurrentHashMap<>();

    @Override
    public boolean supports(EvaluationType type) {
        return SUPPORTED.contains(type);
    }

    @Override
    public String evaluate(EvaluationType type, String inputText) throws AiProviderException {
        if (SIMULATED_FAILURE_TRIGGER.equals(inputText)) {
            throw new AiProviderException("Simulated provider failure (mock adapter)", true);
        }
        if (inputText.startsWith(SIMULATED_FAILURE_TRIGGER + ONCE_SUFFIX)) {
            AtomicInteger counter = onceFailureCounters.computeIfAbsent(inputText, k -> new AtomicInteger(1));
            if (counter.getAndDecrement() > 0) {
                throw new AiProviderException("Simulated transient provider failure (mock adapter)", true);
            }
        }
        // NOT a real score — a deterministic, clearly-fake stub result for plumbing
        // validation.
        return """
                {"mock":true,"type":"%s","inputLength":%d,"score":0}""".formatted(type, inputText.length());
    }
}
