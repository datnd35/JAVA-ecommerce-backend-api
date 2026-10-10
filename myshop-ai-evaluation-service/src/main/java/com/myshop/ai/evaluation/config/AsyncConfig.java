package com.myshop.ai.evaluation.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Enables {@code @Async} processing for
 * {@link com.myshop.ai.evaluation.service.EvaluationService}'s
 * async evaluation mode. Uses Spring Boot's default
 * {@code SimpleAsyncTaskExecutor}-backed
 * auto-configuration — sufficient for Phase 2 scaffolding; revisit pool
 * sizing/backpressure once
 * real provider latency/throughput characteristics are known.
 */
@Configuration
@EnableAsync
public class AsyncConfig {
}
