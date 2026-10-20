package com.aquasentinel.application.dto;

import com.aquasentinel.domain.enums.EvaluationOutcome;
import com.aquasentinel.domain.enums.RecordOrigin;
import com.aquasentinel.domain.evaluation.RuleEvaluationResult;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ReportSnapshot(
        UUID evaluationId,
        Instant generatedAt,
        String sourceCode,
        String sourceName,
        String sourceType,
        String locationText,
        String sampleCode,
        Instant collectedAt,
        RecordOrigin provenance,
        String profileName,
        int profileVersion,
        EvaluationOutcome outcome,
        String evaluatedByUsername,
        Instant evaluatedAt,
        String inputDigest,
        List<RuleEvaluationResult> ruleResults,
        String disclaimer
) {
    public static final String STANDARD_DISCLAIMER =
            "Educational demonstration report. Evaluated strictly against the specified evaluation profile version. " +
            "Not a government-issued drinking water safety certificate.";
}
