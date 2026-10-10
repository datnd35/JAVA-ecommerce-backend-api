package com.myshop.ai.evaluation.domain;

/**
 * Which kind of scoring/evaluation is being requested. Phase 2 scope is
 * deliberately narrow
 * (grammar + pronunciation only) to prove the provider-adapter + sync/async
 * plumbing end-to-end;
 * the full 20-endpoint surface confirmed in source
 * ({@code 02-module-inventory.md}, section C,
 * `ielts-evaluation` — "20 POST endpoints") is explicitly OUT OF SCOPE for this
 * phase and must be
 * added incrementally, one evaluation type at a time, not assumed complete.
 */
public enum EvaluationType {
    GRAMMAR,
    PRONUNCIATION
}
