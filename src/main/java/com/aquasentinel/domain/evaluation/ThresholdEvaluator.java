package com.aquasentinel.domain.evaluation;

import com.aquasentinel.domain.enums.EvaluationOutcome;
import com.aquasentinel.domain.enums.RuleResultOutcome;
import com.aquasentinel.domain.model.ParameterReading;
import com.aquasentinel.domain.model.ProfileVersion;
import com.aquasentinel.domain.model.ThresholdRule;

import java.math.BigDecimal;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Pure domain evaluation engine. Zero DB or UI dependencies.
 * Deterministic and reproducible quality evaluation.
 */
public class ThresholdEvaluator {

    public record EvaluationReport(
            EvaluationOutcome outcome,
            List<RuleEvaluationResult> ruleResults,
            String inputDigest,
            String contextMismatchReason
    ) {}

    public EvaluationReport evaluate(ProfileVersion profile,
                                     String sourceType,
                                     List<ThresholdRule> rules,
                                     Map<UUID, String> paramCodeMap,
                                     List<ParameterReading> readings) {
        Objects.requireNonNull(profile, "profile must not be null");
        Objects.requireNonNull(rules, "rules must not be null");
        Objects.requireNonNull(readings, "readings must not be null");

        // 1. Context check (source type applicability)
        if (sourceType != null && !profile.getAllowedSourceTypesJson().contains(sourceType)) {
            return new EvaluationReport(
                    EvaluationOutcome.NOT_EVALUABLE,
                    List.of(),
                    "",
                    "Source type '" + sourceType + "' is not applicable to profile '" + profile.getName() + "'."
            );
        }

        Map<UUID, ParameterReading> readingMap = readings.stream()
                .collect(Collectors.toMap(ParameterReading::getParameterId, r -> r, (a, b) -> a));

        List<RuleEvaluationResult> results = new ArrayList<>();
        boolean anyFail = false;
        boolean anyRequiredMissing = false;

        // Sort rules deterministically by parameterId for consistent digest calculation
        List<ThresholdRule> sortedRules = new ArrayList<>(rules);
        sortedRules.sort(Comparator.comparing(r -> r.getParameterId().toString()));

        for (ThresholdRule rule : sortedRules) {
            String paramCode = paramCodeMap.getOrDefault(rule.getParameterId(), rule.getParameterId().toString());
            ParameterReading reading = readingMap.get(rule.getParameterId());

            if (reading == null) {
                if (rule.isRequired()) {
                    anyRequiredMissing = true;
                    results.add(new RuleEvaluationResult(
                            rule.getParameterId(), paramCode, RuleResultOutcome.MISSING,
                            null, null, rule.getLowerDecimal(), rule.getUpperDecimal(),
                            rule.isLowerInclusive(), rule.isUpperInclusive(), true,
                            "Required reading for " + paramCode + " is missing."
                    ));
                }
                continue;
            }

            // Check unit match
            if (!rule.getUnit().equalsIgnoreCase(reading.getUnit())) {
                anyFail = true;
                results.add(new RuleEvaluationResult(
                        rule.getParameterId(), paramCode, RuleResultOutcome.FAIL,
                        reading.getValueDecimal(), reading.getUnit(),
                        rule.getLowerDecimal(), rule.getUpperDecimal(),
                        rule.isLowerInclusive(), rule.isUpperInclusive(), rule.isRequired(),
                        "Unit mismatch for " + paramCode + ": expected " + rule.getUnit() + ", observed " + reading.getUnit()
                ));
                continue;
            }

            BigDecimal val = reading.getValueDecimal();
            boolean violated = false;
            StringBuilder explanation = new StringBuilder();

            // Lower bound check
            if (rule.getLowerDecimal() != null) {
                int cmp = val.compareTo(rule.getLowerDecimal());
                if (rule.isLowerInclusive() ? cmp < 0 : cmp <= 0) {
                    violated = true;
                    explanation.append("Observed ").append(val.toPlainString())
                            .append(" is below lower limit ").append(rule.getLowerDecimal().toPlainString())
                            .append(rule.isLowerInclusive() ? " (inclusive)" : " (exclusive)").append("; ");
                }
            }

            // Upper bound check
            if (rule.getUpperDecimal() != null) {
                int cmp = val.compareTo(rule.getUpperDecimal());
                if (rule.isUpperInclusive() ? cmp > 0 : cmp >= 0) {
                    violated = true;
                    explanation.append("Observed ").append(val.toPlainString())
                            .append(" exceeds upper limit ").append(rule.getUpperDecimal().toPlainString())
                            .append(rule.isUpperInclusive() ? " (inclusive)" : " (exclusive)").append("; ");
                }
            }

            if (violated) {
                anyFail = true;
                results.add(new RuleEvaluationResult(
                        rule.getParameterId(), paramCode, RuleResultOutcome.FAIL,
                        val, reading.getUnit(), rule.getLowerDecimal(), rule.getUpperDecimal(),
                        rule.isLowerInclusive(), rule.isUpperInclusive(), rule.isRequired(),
                        explanation.toString().trim()
                ));
            } else {
                results.add(new RuleEvaluationResult(
                        rule.getParameterId(), paramCode, RuleResultOutcome.PASS,
                        val, reading.getUnit(), rule.getLowerDecimal(), rule.getUpperDecimal(),
                        rule.isLowerInclusive(), rule.isUpperInclusive(), rule.isRequired(),
                        "Value is within acceptable limits."
                ));
            }
        }

        // Determine headline outcome per PRD §FR-06:
        // Any available-rule failure yields EXCEEDS_PROFILE;
        // otherwise any missing required rule yields INCOMPLETE;
        // otherwise WITHIN_PROFILE.
        EvaluationOutcome outcome;
        if (anyFail) {
            outcome = EvaluationOutcome.EXCEEDS_PROFILE;
        } else if (anyRequiredMissing) {
            outcome = EvaluationOutcome.INCOMPLETE;
        } else {
            outcome = EvaluationOutcome.WITHIN_PROFILE;
        }

        String digest = computeInputDigest(profile.getId(), sortedRules, readingMap);

        return new EvaluationReport(outcome, results, digest, null);
    }

    private String computeInputDigest(UUID profileId, List<ThresholdRule> rules, Map<UUID, ParameterReading> readings) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(profileId.toString().getBytes(StandardCharsets.UTF_8));
            for (ThresholdRule r : rules) {
                md.update(r.getParameterId().toString().getBytes(StandardCharsets.UTF_8));
                ParameterReading rd = readings.get(r.getParameterId());
                if (rd != null) {
                    md.update(rd.getValueDecimal().toPlainString().getBytes(StandardCharsets.UTF_8));
                    md.update(rd.getUnit().getBytes(StandardCharsets.UTF_8));
                } else {
                    md.update("NULL".getBytes(StandardCharsets.UTF_8));
                }
            }
            return HexFormat.of().formatHex(md.digest());
        } catch (Exception e) {
            return "DIGEST_UNAVAILABLE";
        }
    }
}
