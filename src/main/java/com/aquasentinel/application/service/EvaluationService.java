package com.aquasentinel.application.service;

import com.aquasentinel.application.dto.EvaluationDto;
import com.aquasentinel.application.port.AlertRepository;
import com.aquasentinel.application.port.AuditRepository;
import com.aquasentinel.application.port.EvaluationRepository;
import com.aquasentinel.application.port.InspectionRepository;
import com.aquasentinel.application.port.ParameterRepository;
import com.aquasentinel.application.port.ProfileRepository;
import com.aquasentinel.application.port.ReadingRepository;
import com.aquasentinel.application.port.SampleRepository;
import com.aquasentinel.application.port.SourceRepository;
import com.aquasentinel.application.security.Session;
import com.aquasentinel.domain.enums.AlertState;
import com.aquasentinel.domain.enums.EvaluationOutcome;
import com.aquasentinel.domain.enums.ProfileStatus;
import com.aquasentinel.domain.enums.SampleStatus;
import com.aquasentinel.domain.evaluation.ThresholdEvaluator;
import com.aquasentinel.domain.exception.AuthorizationException;
import com.aquasentinel.domain.exception.ValidationException;
import com.aquasentinel.domain.model.Inspection;
import com.aquasentinel.domain.model.ParameterDefinition;
import com.aquasentinel.domain.model.ParameterReading;
import com.aquasentinel.domain.model.ProfileVersion;
import com.aquasentinel.domain.model.ThresholdRule;
import com.aquasentinel.domain.model.WaterSample;
import com.aquasentinel.domain.model.WaterSource;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

public class EvaluationService {
    private final EvaluationRepository evaluationRepository;
    private final ProfileRepository profileRepository;
    private final SampleRepository sampleRepository;
    private final ReadingRepository readingRepository;
    private final ParameterRepository parameterRepository;
    private final InspectionRepository inspectionRepository;
    private final SourceRepository sourceRepository;
    private final AlertRepository alertRepository;
    private final AuditRepository auditRepository;
    private final ThresholdEvaluator thresholdEvaluator;

    public EvaluationService(EvaluationRepository evaluationRepository,
                             ProfileRepository profileRepository,
                             SampleRepository sampleRepository,
                             ReadingRepository readingRepository,
                             ParameterRepository parameterRepository,
                             InspectionRepository inspectionRepository,
                             SourceRepository sourceRepository,
                             AlertRepository alertRepository,
                             AuditRepository auditRepository,
                             ThresholdEvaluator thresholdEvaluator) {
        this.evaluationRepository = evaluationRepository;
        this.profileRepository = profileRepository;
        this.sampleRepository = sampleRepository;
        this.readingRepository = readingRepository;
        this.parameterRepository = parameterRepository;
        this.inspectionRepository = inspectionRepository;
        this.sourceRepository = sourceRepository;
        this.alertRepository = alertRepository;
        this.auditRepository = auditRepository;
        this.thresholdEvaluator = thresholdEvaluator;
    }

    public EvaluationDto evaluate(Session session, UUID sampleId, UUID profileVersionId) {
        if (!session.isAnalystOrAdmin()) {
            throw new AuthorizationException("Only analysts or administrators can evaluate samples.");
        }

        WaterSample sample = sampleRepository.findById(sampleId)
                .orElseThrow(() -> new ValidationException("Sample not found: " + sampleId));

        ProfileVersion profile = profileRepository.findById(profileVersionId)
                .orElseThrow(() -> new ValidationException("Profile version not found: " + profileVersionId));

        if (profile.getStatus() != ProfileStatus.PUBLISHED) {
            throw new ValidationException("Cannot evaluate against an un-published profile version.");
        }

        // Idempotency: Return existing evaluation if already evaluated against this profile version
        Optional<EvaluationRepository.StoredEvaluation> existing =
                evaluationRepository.findBySampleAndProfile(sampleId, profileVersionId);
        if (existing.isPresent()) {
            EvaluationRepository.StoredEvaluation se = existing.get();
            return new EvaluationDto(
                    se.id(), se.sampleId(), se.profileVersionId(), profile.getName(),
                    profile.getVersionNumber(), se.outcome(), se.evaluatedBy(),
                    se.evaluatedAt(), se.inputDigest(), List.of(), null
            );
        }

        // Validate lifecycle state
        if (sample.getStatus() != SampleStatus.SUBMITTED && sample.getStatus() != SampleStatus.EVALUATED) {
            throw new ValidationException("Sample must be in SUBMITTED or EVALUATED state to evaluate. Current: " + sample.getStatus());
        }

        // Load source to check applicability
        Inspection inspection = inspectionRepository.findById(sample.getInspectionId())
                .orElseThrow(() -> new ValidationException("Inspection not found: " + sample.getInspectionId()));
        WaterSource source = sourceRepository.findById(inspection.getSourceId())
                .orElseThrow(() -> new ValidationException("Source not found: " + inspection.getSourceId()));

        List<ThresholdRule> rules = profileRepository.findRulesByProfileVersionId(profileVersionId);
        if (rules.isEmpty()) {
            throw new ValidationException("Profile version has no defined threshold rules.");
        }

        List<ParameterReading> readings = readingRepository.findBySampleId(sampleId);
        Map<UUID, String> paramCodeMap = parameterRepository.findAll().stream()
                .collect(Collectors.toMap(ParameterDefinition::getId, ParameterDefinition::getCode));

        // Pure evaluation
        ThresholdEvaluator.EvaluationReport report = thresholdEvaluator.evaluate(
                profile, source.getSourceType().name(), rules, paramCodeMap, readings);

        if (report.outcome() == EvaluationOutcome.NOT_EVALUABLE) {
            return new EvaluationDto(
                    null, sampleId, profileVersionId, profile.getName(), profile.getVersionNumber(),
                    EvaluationOutcome.NOT_EVALUABLE, session.userId(), Instant.now(), "",
                    List.of(), report.contextMismatchReason()
            );
        }

        UUID evaluationId = UUID.randomUUID();
        Instant now = Instant.now();
        EvaluationRepository.StoredEvaluation storedEval = new EvaluationRepository.StoredEvaluation(
                evaluationId, sampleId, profileVersionId, report.outcome(),
                session.userId(), now, report.inputDigest()
        );

        // 1. Save evaluation and rule snapshots
        evaluationRepository.save(storedEval, report.ruleResults());

        // 2. Transition sample state to EVALUATED
        if (sample.getStatus() != SampleStatus.EVALUATED) {
            WaterSample evaluatedSample = new WaterSample(
                    sample.getId(), sample.getCode(), sample.getInspectionId(), SampleStatus.EVALUATED,
                    sample.getCollectedAt(), sample.getReceivedAt(), sample.getCollectedBy(),
                    sample.getOrigin(), sample.getImportBatchId(), sample.getSupersedesSampleId(),
                    sample.getCreatedAt(), sample.getVersion()
            );
            sampleRepository.update(evaluatedSample);
        }

        // 3. Create Alert if EXCEEDS_PROFILE
        if (report.outcome() == EvaluationOutcome.EXCEEDS_PROFILE) {
            AlertRepository.StoredAlert alert = new AlertRepository.StoredAlert(
                    UUID.randomUUID(), evaluationId, AlertState.OPEN, null, null, null, 1
            );
            alertRepository.save(alert);
        }

        // 4. Audit event
        auditRepository.record(session.userId(), "SAMPLE_EVALUATED", "EVALUATION", evaluationId.toString(), now,
                null, "{\"outcome\":\"" + report.outcome().name() + "\",\"profile\":\"" + profile.getName() + "\"}");

        return new EvaluationDto(
                evaluationId, sampleId, profileVersionId, profile.getName(), profile.getVersionNumber(),
                report.outcome(), session.userId(), now, report.inputDigest(),
                report.ruleResults(), null
        );
    }
}
