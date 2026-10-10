package com.myshop.ai.evaluation.service;

import com.myshop.ai.evaluation.domain.EvaluationType;
import com.myshop.ai.evaluation.provider.AiProviderAdapter;
import java.util.List;
import java.util.NoSuchElementException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Picks the right {@link AiProviderAdapter} for a given {@link EvaluationType}.
 * Kept separate from
 * {@link EvaluationService} so adding a new provider/type combination never
 * requires touching the
 * orchestration logic — only registering a new {@code AiProviderAdapter} bean.
 */
@Component
@RequiredArgsConstructor
public class AiProviderDispatcher {

    private final List<AiProviderAdapter> adapters;

    public AiProviderAdapter forType(EvaluationType type) {
        return adapters.stream()
                .filter(adapter -> adapter.supports(type))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("No AiProviderAdapter supports type: " + type));
    }
}
