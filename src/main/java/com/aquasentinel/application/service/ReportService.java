package com.aquasentinel.application.service;

import com.aquasentinel.application.dto.ReportSnapshot;
import com.aquasentinel.application.port.EvaluationRepository;
import com.aquasentinel.application.port.InspectionRepository;
import com.aquasentinel.application.port.ProfileRepository;
import com.aquasentinel.application.port.ReportExporter;
import com.aquasentinel.application.port.SampleRepository;
import com.aquasentinel.application.port.SourceRepository;
import com.aquasentinel.application.port.UserRepository;
import com.aquasentinel.application.security.Session;
import com.aquasentinel.domain.enums.RecordOrigin;
import com.aquasentinel.domain.evaluation.RuleEvaluationResult;
import com.aquasentinel.domain.exception.ValidationException;
import com.aquasentinel.domain.model.Inspection;
import com.aquasentinel.domain.model.ProfileVersion;
import com.aquasentinel.domain.model.UserAccount;
import com.aquasentinel.domain.model.WaterSample;
import com.aquasentinel.domain.model.WaterSource;

import java.io.OutputStream;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class ReportService {
    private final EvaluationRepository evaluationRepository;
    private final ProfileRepository profileRepository;
    private final SampleRepository sampleRepository;
    private final InspectionRepository inspectionRepository;
    private final SourceRepository sourceRepository;
    private final UserRepository userRepository;

    public ReportService(EvaluationRepository evaluationRepository,
                         ProfileRepository profileRepository,
                         SampleRepository sampleRepository,
                         InspectionRepository inspectionRepository,
                         SourceRepository sourceRepository,
                         UserRepository userRepository) {
        this.evaluationRepository = Objects.requireNonNull(evaluationRepository, "evaluationRepository");
        this.profileRepository = Objects.requireNonNull(profileRepository, "profileRepository");
        this.sampleRepository = Objects.requireNonNull(sampleRepository, "sampleRepository");
        this.inspectionRepository = Objects.requireNonNull(inspectionRepository, "inspectionRepository");
        this.sourceRepository = Objects.requireNonNull(sourceRepository, "sourceRepository");
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository");
    }

    public ReportSnapshot createSnapshot(Session session, UUID evaluationId) {
        EvaluationRepository.StoredEvaluation eval = evaluationRepository.findById(evaluationId)
                .orElseThrow(() -> new ValidationException("Evaluation not found: " + evaluationId));

        ProfileVersion profile = profileRepository.findById(eval.profileVersionId())
                .orElseThrow(() -> new ValidationException("Profile not found: " + eval.profileVersionId()));

        WaterSample sample = sampleRepository.findById(eval.sampleId())
                .orElseThrow(() -> new ValidationException("Sample not found: " + eval.sampleId()));

        Inspection inspection = inspectionRepository.findById(sample.getInspectionId())
                .orElseThrow(() -> new ValidationException("Inspection not found: " + sample.getInspectionId()));

        WaterSource source = sourceRepository.findById(inspection.getSourceId())
                .orElseThrow(() -> new ValidationException("Source not found: " + inspection.getSourceId()));

        String evalUsername = userRepository.findById(eval.evaluatedBy())
                .map(UserAccount::getUsername)
                .orElse("Unknown");

        List<RuleEvaluationResult> rules = evaluationRepository.findRuleResultsByEvaluationId(evaluationId);

        return new ReportSnapshot(
                eval.id(),
                Instant.now(),
                source.getCode(),
                source.getName(),
                source.getSourceType().name(),
                source.getLocationText(),
                sample.getCode(),
                sample.getCollectedAt(),
                sample.getOrigin(),
                profile.getName(),
                profile.getVersionNumber(),
                eval.outcome(),
                evalUsername,
                eval.evaluatedAt(),
                eval.inputDigest(),
                rules,
                ReportSnapshot.STANDARD_DISCLAIMER
        );
    }

    public void exportEvaluationReport(Session session, UUID evaluationId, ReportExporter exporter, OutputStream out) {
        ReportSnapshot snapshot = createSnapshot(session, evaluationId);
        exporter.export(snapshot, out);
    }
}
