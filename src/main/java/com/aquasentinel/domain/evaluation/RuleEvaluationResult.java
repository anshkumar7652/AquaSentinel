package com.aquasentinel.domain.evaluation;

import com.aquasentinel.domain.enums.RuleResultOutcome;

import java.math.BigDecimal;
import java.util.UUID;

public record RuleEvaluationResult(
        UUID parameterId,
        String parameterCode,
        RuleResultOutcome outcome,
        BigDecimal observedValue,
        String observedUnit,
        BigDecimal lowerLimit,
        BigDecimal upperLimit,
        boolean lowerInclusive,
        boolean upperInclusive,
        boolean required,
        String explanation
) {}
