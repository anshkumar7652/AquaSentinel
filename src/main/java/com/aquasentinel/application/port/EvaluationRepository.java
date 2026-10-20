package com.aquasentinel.application.port;

import com.aquasentinel.domain.enums.EvaluationOutcome;
import com.aquasentinel.domain.evaluation.RuleEvaluationResult;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EvaluationRepository {
    record StoredEvaluation(
            UUID id,
            UUID sampleId,
            UUID profileVersionId,
            EvaluationOutcome outcome,
            UUID evaluatedBy,
            Instant evaluatedAt,
            String inputDigest
    ) {}

    Optional<StoredEvaluation> findBySampleAndProfile(UUID sampleId, UUID profileVersionId);
    Optional<StoredEvaluation> findById(UUID id);
    List<StoredEvaluation> findAll();
    List<RuleEvaluationResult> findRuleResultsByEvaluationId(UUID evaluationId);
    void save(StoredEvaluation evaluation, List<RuleEvaluationResult> ruleResults);
}
