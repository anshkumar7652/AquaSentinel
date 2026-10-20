package com.aquasentinel.domain.evaluation;

import com.aquasentinel.domain.enums.EvaluationOutcome;
import com.aquasentinel.domain.enums.ProfileStatus;
import com.aquasentinel.domain.enums.RecordOrigin;
import com.aquasentinel.domain.enums.RuleResultOutcome;
import com.aquasentinel.domain.model.ParameterReading;
import com.aquasentinel.domain.model.ProfileVersion;
import com.aquasentinel.domain.model.ThresholdRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ThresholdEvaluatorTest {

    private ThresholdEvaluator evaluator;
    private ProfileVersion profile;
    private UUID phId;
    private UUID turbId;
    private UUID doId;
    private UUID condId;

    @BeforeEach
    void setUp() {
        evaluator = new ThresholdEvaluator();

        phId = UUID.randomUUID();
        turbId = UUID.randomUUID();
        doId = UUID.randomUUID();
        condId = UUID.randomUUID();

        profile = new ProfileVersion(
                UUID.randomUUID(),
                "SURFACE_DEMO",
                1,
                "Surface Water Demo Profile",
                ProfileStatus.PUBLISHED,
                "Surface testing",
                "[\"RIVER\", \"LAKE\"]",
                "POINT_SAMPLE",
                RecordOrigin.MANUAL_ENTRY,
                "Fictional bounds",
                null,
                Instant.now()
        );
    }

    private List<ThresholdRule> createRules() {
        return List.of(
                // pH: 6.5 - 8.5 inclusive, required
                new ThresholdRule(UUID.randomUUID(), profile.getId(), phId, new BigDecimal("6.5"), new BigDecimal("8.5"), true, true, true, "1"),
                // Turbidity: <= 5.0 NTU inclusive, required
                new ThresholdRule(UUID.randomUUID(), profile.getId(), turbId, null, new BigDecimal("5.0"), true, true, true, "NTU"),
                // Dissolved Oxygen: >= 5.0 mg/L inclusive, required
                new ThresholdRule(UUID.randomUUID(), profile.getId(), doId, new BigDecimal("5.0"), null, true, true, true, "mg/L"),
                // Conductivity: <= 1500 uS/cm inclusive, optional
                new ThresholdRule(UUID.randomUUID(), profile.getId(), condId, null, new BigDecimal("1500.0"), true, true, false, "uS/cm")
        );
    }

    @Test
    void testAllPassingYieldsWithinProfile() {
        List<ThresholdRule> rules = createRules();
        Map<UUID, String> paramCodes = Map.of(phId, "PH", turbId, "TURB", doId, "DO", condId, "COND");

        List<ParameterReading> readings = List.of(
                new ParameterReading(UUID.randomUUID(), UUID.randomUUID(), phId, new BigDecimal("7.4"), "1", UUID.randomUUID(), Instant.now(), RecordOrigin.MANUAL_ENTRY, null),
                new ParameterReading(UUID.randomUUID(), UUID.randomUUID(), turbId, new BigDecimal("2.1"), "NTU", UUID.randomUUID(), Instant.now(), RecordOrigin.MANUAL_ENTRY, null),
                new ParameterReading(UUID.randomUUID(), UUID.randomUUID(), doId, new BigDecimal("6.5"), "mg/L", UUID.randomUUID(), Instant.now(), RecordOrigin.MANUAL_ENTRY, null)
        );

        ThresholdEvaluator.EvaluationReport report = evaluator.evaluate(profile, "RIVER", rules, paramCodes, readings);
        assertEquals(EvaluationOutcome.WITHIN_PROFILE, report.outcome());
        assertNotNull(report.inputDigest());
        assertEquals(3, report.ruleResults().size());
        assertTrue(report.ruleResults().stream().allMatch(r -> r.outcome() == RuleResultOutcome.PASS));
    }

    @Test
    void testBoundaryInclusiveCheck() {
        List<ThresholdRule> rules = createRules();
        Map<UUID, String> paramCodes = Map.of(phId, "PH", turbId, "TURB", doId, "DO", condId, "COND");

        // Exactly on boundaries: pH 8.5 (upper bound), DO 5.0 (lower bound), Turbidity 5.0 (upper bound)
        List<ParameterReading> readings = List.of(
                new ParameterReading(UUID.randomUUID(), UUID.randomUUID(), phId, new BigDecimal("8.5"), "1", UUID.randomUUID(), Instant.now(), RecordOrigin.MANUAL_ENTRY, null),
                new ParameterReading(UUID.randomUUID(), UUID.randomUUID(), turbId, new BigDecimal("5.0"), "NTU", UUID.randomUUID(), Instant.now(), RecordOrigin.MANUAL_ENTRY, null),
                new ParameterReading(UUID.randomUUID(), UUID.randomUUID(), doId, new BigDecimal("5.0"), "mg/L", UUID.randomUUID(), Instant.now(), RecordOrigin.MANUAL_ENTRY, null)
        );

        ThresholdEvaluator.EvaluationReport report = evaluator.evaluate(profile, "RIVER", rules, paramCodes, readings);
        assertEquals(EvaluationOutcome.WITHIN_PROFILE, report.outcome(), "Exact inclusive bounds must pass");
    }

    @Test
    void testViolatedRuleYieldsExceedsProfile() {
        List<ThresholdRule> rules = createRules();
        Map<UUID, String> paramCodes = Map.of(phId, "PH", turbId, "TURB", doId, "DO", condId, "COND");

        // Turbidity 7.5 exceeds 5.0
        List<ParameterReading> readings = List.of(
                new ParameterReading(UUID.randomUUID(), UUID.randomUUID(), phId, new BigDecimal("7.0"), "1", UUID.randomUUID(), Instant.now(), RecordOrigin.MANUAL_ENTRY, null),
                new ParameterReading(UUID.randomUUID(), UUID.randomUUID(), turbId, new BigDecimal("7.5"), "NTU", UUID.randomUUID(), Instant.now(), RecordOrigin.MANUAL_ENTRY, null),
                new ParameterReading(UUID.randomUUID(), UUID.randomUUID(), doId, new BigDecimal("6.0"), "mg/L", UUID.randomUUID(), Instant.now(), RecordOrigin.MANUAL_ENTRY, null)
        );

        ThresholdEvaluator.EvaluationReport report = evaluator.evaluate(profile, "RIVER", rules, paramCodes, readings);
        assertEquals(EvaluationOutcome.EXCEEDS_PROFILE, report.outcome());
        assertTrue(report.ruleResults().stream().anyMatch(r -> r.outcome() == RuleResultOutcome.FAIL && "TURB".equals(r.parameterCode())));
    }

    @Test
    void testMissingRequiredParameterYieldsIncomplete() {
        List<ThresholdRule> rules = createRules();
        Map<UUID, String> paramCodes = Map.of(phId, "PH", turbId, "TURB", doId, "DO", condId, "COND");

        // Missing DO (which is required)
        List<ParameterReading> readings = List.of(
                new ParameterReading(UUID.randomUUID(), UUID.randomUUID(), phId, new BigDecimal("7.0"), "1", UUID.randomUUID(), Instant.now(), RecordOrigin.MANUAL_ENTRY, null),
                new ParameterReading(UUID.randomUUID(), UUID.randomUUID(), turbId, new BigDecimal("3.0"), "NTU", UUID.randomUUID(), Instant.now(), RecordOrigin.MANUAL_ENTRY, null)
        );

        ThresholdEvaluator.EvaluationReport report = evaluator.evaluate(profile, "RIVER", rules, paramCodes, readings);
        assertEquals(EvaluationOutcome.INCOMPLETE, report.outcome());
        assertTrue(report.ruleResults().stream().anyMatch(r -> r.outcome() == RuleResultOutcome.MISSING && "DO".equals(r.parameterCode())));
    }

    @Test
    void testViolationDominatesHeadlineOutcomeEvenWhenDataIsMissing() {
        List<ThresholdRule> rules = createRules();
        Map<UUID, String> paramCodes = Map.of(phId, "PH", turbId, "TURB", doId, "DO", condId, "COND");

        // pH 9.2 (FAIL) and DO is missing (MISSING)
        List<ParameterReading> readings = List.of(
                new ParameterReading(UUID.randomUUID(), UUID.randomUUID(), phId, new BigDecimal("9.2"), "1", UUID.randomUUID(), Instant.now(), RecordOrigin.MANUAL_ENTRY, null),
                new ParameterReading(UUID.randomUUID(), UUID.randomUUID(), turbId, new BigDecimal("2.0"), "NTU", UUID.randomUUID(), Instant.now(), RecordOrigin.MANUAL_ENTRY, null)
        );

        ThresholdEvaluator.EvaluationReport report = evaluator.evaluate(profile, "RIVER", rules, paramCodes, readings);
        assertEquals(EvaluationOutcome.EXCEEDS_PROFILE, report.outcome(), "FAIL must dominate headline outcome");
        assertTrue(report.ruleResults().stream().anyMatch(r -> r.outcome() == RuleResultOutcome.FAIL));
        assertTrue(report.ruleResults().stream().anyMatch(r -> r.outcome() == RuleResultOutcome.MISSING));
    }

    @Test
    void testSourceTypeMismatchYieldsNotEvaluable() {
        List<ThresholdRule> rules = createRules();
        Map<UUID, String> paramCodes = Map.of(phId, "PH");

        // Profile only allows RIVER and LAKE; evaluating against GROUNDWATER_WELL yields NOT_EVALUABLE
        ThresholdEvaluator.EvaluationReport report = evaluator.evaluate(profile, "GROUNDWATER_WELL", rules, paramCodes, List.of());
        assertEquals(EvaluationOutcome.NOT_EVALUABLE, report.outcome());
        assertNotNull(report.contextMismatchReason());
    }
}
