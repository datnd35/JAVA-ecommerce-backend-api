package com.myshop.ai.evaluation.web;

import com.myshop.ai.evaluation.domain.Evaluation;
import com.myshop.ai.evaluation.service.EvaluationService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * REST entry point for the AI Evaluation Service (Phase 2 scaffold).
 * <p>
 * Auth is NOT implemented yet — same deferred-to-later-phase stance taken for
 * the Media Service
 * in Phase 1. Do not expose this service to untrusted traffic as-is.
 */
@RestController
@RequestMapping("/evaluations")
@RequiredArgsConstructor
public class EvaluationController {

    private final EvaluationService evaluationService;

    /**
     * Mode A — Synchronous: blocks until the provider responds or retries are
     * exhausted.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EvaluationResponse evaluateSync(@Valid @RequestBody EvaluateRequest request) {
        Evaluation evaluation = evaluationService.evaluateSync(
                request.type(), request.provider(), request.inputText());
        return EvaluationResponse.from(evaluation);
    }

    /**
     * Mode B — Asynchronous: returns immediately with a PENDING evaluation; poll
     * GET below.
     */
    @PostMapping("/async")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public EvaluationResponse submitAsync(@Valid @RequestBody EvaluateRequest request) {
        Evaluation evaluation = evaluationService.submitAsync(
                request.type(), request.provider(), request.inputText());
        return EvaluationResponse.from(evaluation);
    }

    @GetMapping("/{id}")
    public ResponseEntity<EvaluationResponse> getEvaluation(@PathVariable UUID id) {
        try {
            return ResponseEntity.ok(EvaluationResponse.from(evaluationService.getEvaluation(id)));
        } catch (java.util.NoSuchElementException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Evaluation not found: " + id);
        }
    }
}
