package com.aquasentinel.application.dto;

import com.aquasentinel.domain.enums.EvaluationOutcome;
import com.aquasentinel.domain.evaluation.RuleEvaluationResult;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record EvaluationDto(
        UUID id,
        UUID sampleId,
        UUID profileVersionId,
        String profileName,
        int profileVersionNumber,
        EvaluationOutcome outcome,
        UUID evaluatedBy,
        Instant evaluatedAt,
        String inputDigest,
        List<RuleEvaluationResult> ruleResults,
        String contextMismatchReason
) {}
