package com.myshop.ai.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.myshop.ai.evaluation.domain.AiProviderType;
import com.myshop.ai.evaluation.domain.Evaluation;
import com.myshop.ai.evaluation.domain.EvaluationStatus;
import com.myshop.ai.evaluation.domain.EvaluationType;
import com.myshop.ai.evaluation.provider.MockAiProviderAdapter;
import com.myshop.ai.evaluation.service.EvaluationService;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Characterization tests for {@link EvaluationService} against the
 * {@link MockAiProviderAdapter}.
 * <p>
 * <b>Important:</b> these tests specify a NEW behavior (this is NOT a verified
 * port of any
 * existing Open4Talk/NestJS test suite — no such reference test was found
 * during discovery; see
 * {@code docs/architecture/05-runtime-flows.md} Flow 3 "uncertainties"). They
 * exist to lock in
 * this Phase 2 scaffold's documented retry/failure semantics going forward.
 * <p>
 * Only Postgres is required here (no RabbitMQ), unlike the Media Service tests,
 * since AI
 * Evaluation Phase 2 scope has no messaging component.
 */
@Testcontainers
@SpringBootTest
class EvaluationServiceCharacterizationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("myshop_ai_test")
            .withUsername("myshop_ai")
            .withPassword("myshop_ai");

    @DynamicPropertySource
    static void overrideDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private EvaluationService evaluationService;

    @Test
    void evaluateSync_succeeds_withMockAdapter() {
        Evaluation result = evaluationService.evaluateSync(
                EvaluationType.GRAMMAR, AiProviderType.OPENAI, "The cat sit on the mat.");

        assertThat(result.getStatus()).isEqualTo(EvaluationStatus.SUCCEEDED);
        assertThat(result.getResultJson()).contains("\"mock\":true");
        assertThat(result.getFailureReason()).isNull();
    }

    @Test
    void evaluateSync_retriesThenSucceeds_onSimulatedTransientFailure() {
        // MockAiProviderAdapter throws a retryable AiProviderException exactly once
        // when the
        // input text starts with SIMULATED_FAILURE_TRIGGER, then succeeds on the next
        // attempt —
        // this exercises the bounded retry loop in EvaluationService#runEvaluation.
        String input = MockAiProviderAdapter.SIMULATED_FAILURE_TRIGGER + "-once:The cat sit on the mat.";

        Evaluation result = evaluationService.evaluateSync(
                EvaluationType.GRAMMAR, AiProviderType.OPENAI, input);

        assertThat(result.getStatus()).isEqualTo(EvaluationStatus.SUCCEEDED);
    }

    @Test
    void evaluateSync_marksFailed_whenRetriesExhausted() {
        // Always-failing trigger (no "-once" suffix) exhausts all configured attempts.
        String input = MockAiProviderAdapter.SIMULATED_FAILURE_TRIGGER;

        Evaluation result = evaluationService.evaluateSync(
                EvaluationType.GRAMMAR, AiProviderType.OPENAI, input);

        assertThat(result.getStatus()).isEqualTo(EvaluationStatus.FAILED);
        assertThat(result.getFailureReason()).isNotBlank();
    }

    @Test
    void submitAsync_eventuallyTransitionsToSucceeded() {
        Evaluation submitted = evaluationService.submitAsync(
                EvaluationType.PRONUNCIATION, AiProviderType.AZURE_SPEECH, "hello world");

        assertThat(submitted.getStatus()).isIn(EvaluationStatus.PENDING, EvaluationStatus.IN_PROGRESS);

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            Evaluation latest = evaluationService.getEvaluation(submitted.getId());
            assertThat(latest.getStatus()).isEqualTo(EvaluationStatus.SUCCEEDED);
        });
    }
}
