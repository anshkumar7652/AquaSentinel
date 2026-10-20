package com.aquasentinel.application.service;

import com.aquasentinel.application.dto.ParameterDto;
import com.aquasentinel.application.dto.ReadingDto;
import com.aquasentinel.application.dto.SampleDto;
import com.aquasentinel.application.port.AuditRepository;
import com.aquasentinel.application.port.InspectionRepository;
import com.aquasentinel.application.port.ParameterRepository;
import com.aquasentinel.application.port.ReadingRepository;
import com.aquasentinel.application.port.SampleRepository;
import com.aquasentinel.application.security.Session;
import com.aquasentinel.domain.enums.RecordOrigin;
import com.aquasentinel.domain.enums.Role;
import com.aquasentinel.domain.enums.SampleStatus;
import com.aquasentinel.domain.exception.AuthorizationException;
import com.aquasentinel.domain.exception.ValidationException;
import com.aquasentinel.domain.model.Inspection;
import com.aquasentinel.domain.model.ParameterDefinition;
import com.aquasentinel.domain.model.ParameterReading;
import com.aquasentinel.domain.model.WaterSample;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

public class SampleService {
    private final SampleRepository sampleRepository;
    private final ReadingRepository readingRepository;
    private final ParameterRepository parameterRepository;
    private final InspectionRepository inspectionRepository;
    private final AuditRepository auditRepository;

    public SampleService(SampleRepository sampleRepository,
                         ReadingRepository readingRepository,
                         ParameterRepository parameterRepository,
                         InspectionRepository inspectionRepository,
                         AuditRepository auditRepository) {
        this.sampleRepository = sampleRepository;
        this.readingRepository = readingRepository;
        this.parameterRepository = parameterRepository;
        this.inspectionRepository = inspectionRepository;
        this.auditRepository = auditRepository;
    }

    public SampleDto registerSample(Session session, String code, UUID inspectionId, RecordOrigin origin) {
        if (session.role() == Role.VIEWER) {
            throw new AuthorizationException("Viewers cannot register samples.");
        }
        if (code == null || code.trim().isEmpty()) {
            throw new ValidationException("Sample code cannot be empty.");
        }

        String normalizedCode = code.trim().toUpperCase();
        if (sampleRepository.findByCode(normalizedCode).isPresent()) {
            throw new ValidationException("Sample with code '" + normalizedCode + "' already exists.");
        }

        Inspection inspection = inspectionRepository.findById(inspectionId)
                .orElseThrow(() -> new ValidationException("Inspection not found: " + inspectionId));

        UUID sampleId = UUID.randomUUID();
        Instant now = Instant.now();
        WaterSample sample = new WaterSample(
                sampleId,
                normalizedCode,
                inspection.getId(),
                SampleStatus.REGISTERED,
                null,
                null,
                null,
                origin != null ? origin : RecordOrigin.MANUAL_ENTRY,
                null,
                null,
                now,
                1
        );

        sampleRepository.save(sample);
        auditRepository.record(session.userId(), "SAMPLE_REGISTERED", "SAMPLE", sampleId.toString(), now, null,
                "{\"code\":\"" + normalizedCode + "\",\"inspectionId\":\"" + inspectionId + "\"}");

        return SampleDto.from(sample);
    }

    public void recordCollection(Session session, UUID sampleId, Instant collectedAt, UUID collectedBy) {
        if (session.role() == Role.VIEWER) {
            throw new AuthorizationException("Unauthorized to update sample collection.");
        }
        WaterSample sample = sampleRepository.findById(sampleId)
                .orElseThrow(() -> new ValidationException("Sample not found: " + sampleId));

        if (sample.getStatus() != SampleStatus.REGISTERED) {
            throw new ValidationException("Sample must be in REGISTERED state to record collection.");
        }
        if (collectedAt == null) {
            throw new ValidationException("Collection timestamp cannot be null.");
        }

        WaterSample updated = new WaterSample(
                sample.getId(),
                sample.getCode(),
                sample.getInspectionId(),
                SampleStatus.COLLECTED,
                collectedAt,
                sample.getReceivedAt(),
                collectedBy != null ? collectedBy : session.userId(),
                sample.getOrigin(),
                sample.getImportBatchId(),
                sample.getSupersedesSampleId(),
                sample.getCreatedAt(),
                sample.getVersion()
        );

        sampleRepository.update(updated);
        auditRepository.record(session.userId(), "SAMPLE_COLLECTED", "SAMPLE", sampleId.toString(), Instant.now(),
                "{\"status\":\"REGISTERED\"}", "{\"status\":\"COLLECTED\"}");
    }

    public void recordReceipt(Session session, UUID sampleId, Instant receivedAt) {
        if (session.role() == Role.VIEWER) {
            throw new AuthorizationException("Unauthorized to record sample receipt.");
        }
        WaterSample sample = sampleRepository.findById(sampleId)
                .orElseThrow(() -> new ValidationException("Sample not found: " + sampleId));

        if (sample.getStatus() != SampleStatus.COLLECTED) {
            throw new ValidationException("Sample must be in COLLECTED state to record laboratory receipt.");
        }
        if (receivedAt == null) {
            throw new ValidationException("Receipt timestamp cannot be null.");
        }
        if (sample.getCollectedAt() != null && receivedAt.isBefore(sample.getCollectedAt())) {
            throw new ValidationException("Laboratory receipt time cannot precede collection time.");
        }

        WaterSample updated = new WaterSample(
                sample.getId(),
                sample.getCode(),
                sample.getInspectionId(),
                SampleStatus.RECEIVED,
                sample.getCollectedAt(),
                receivedAt,
                sample.getCollectedBy(),
                sample.getOrigin(),
                sample.getImportBatchId(),
                sample.getSupersedesSampleId(),
                sample.getCreatedAt(),
                sample.getVersion()
        );

        sampleRepository.update(updated);
        auditRepository.record(session.userId(), "SAMPLE_RECEIVED", "SAMPLE", sampleId.toString(), Instant.now(),
                "{\"status\":\"COLLECTED\"}", "{\"status\":\"RECEIVED\"}");
    }

    public void enterDraftReadings(Session session, UUID sampleId, Map<String, BigDecimal> readingsByParamCode) {
        if (!session.isAnalystOrAdmin()) {
            throw new AuthorizationException("Only analysts or administrators can enter sample readings.");
        }
        WaterSample sample = sampleRepository.findById(sampleId)
                .orElseThrow(() -> new ValidationException("Sample not found: " + sampleId));

        if (sample.getStatus() != SampleStatus.RECEIVED && sample.getStatus() != SampleStatus.ANALYSIS_DRAFT) {
            throw new ValidationException("Readings can only be entered on RECEIVED or ANALYSIS_DRAFT samples.");
        }

        readingRepository.deleteBySampleId(sampleId);

        Instant now = Instant.now();
        for (Map.Entry<String, BigDecimal> entry : readingsByParamCode.entrySet()) {
            String paramCode = entry.getKey();
            BigDecimal value = entry.getValue();

            ParameterDefinition param = parameterRepository.findByCode(paramCode)
                    .orElseThrow(() -> new ValidationException("Unknown parameter code: " + paramCode));

            validateReadingValue(param, value);

            ParameterReading reading = new ParameterReading(
                    UUID.randomUUID(),
                    sampleId,
                    param.getId(),
                    value,
                    param.getCanonicalUnit(),
                    session.userId(),
                    now,
                    sample.getOrigin(),
                    null
            );
            readingRepository.save(reading);
        }

        if (sample.getStatus() != SampleStatus.ANALYSIS_DRAFT) {
            WaterSample updated = new WaterSample(
                    sample.getId(),
                    sample.getCode(),
                    sample.getInspectionId(),
                    SampleStatus.ANALYSIS_DRAFT,
                    sample.getCollectedAt(),
                    sample.getReceivedAt(),
                    sample.getCollectedBy(),
                    sample.getOrigin(),
                    sample.getImportBatchId(),
                    sample.getSupersedesSampleId(),
                    sample.getCreatedAt(),
                    sample.getVersion()
            );
            sampleRepository.update(updated);
        }

        auditRepository.record(session.userId(), "READINGS_UPDATED", "SAMPLE", sampleId.toString(), now, null,
                "{\"count\":" + readingsByParamCode.size() + "}");
    }

    public void submitSample(Session session, UUID sampleId) {
        if (!session.isAnalystOrAdmin()) {
            throw new AuthorizationException("Only analysts or administrators can submit sample analysis.");
        }
        WaterSample sample = sampleRepository.findById(sampleId)
                .orElseThrow(() -> new ValidationException("Sample not found: " + sampleId));

        if (sample.getStatus() != SampleStatus.ANALYSIS_DRAFT) {
            throw new ValidationException("Only samples in ANALYSIS_DRAFT state can be submitted.");
        }

        List<ParameterReading> readings = readingRepository.findBySampleId(sampleId);
        if (readings.isEmpty()) {
            throw new ValidationException("Cannot submit sample without any entered readings.");
        }

        WaterSample updated = new WaterSample(
                sample.getId(),
                sample.getCode(),
                sample.getInspectionId(),
                SampleStatus.SUBMITTED,
                sample.getCollectedAt(),
                sample.getReceivedAt(),
                sample.getCollectedBy(),
                sample.getOrigin(),
                sample.getImportBatchId(),
                sample.getSupersedesSampleId(),
                sample.getCreatedAt(),
                sample.getVersion()
        );

        sampleRepository.update(updated);
        auditRepository.record(session.userId(), "SAMPLE_SUBMITTED", "SAMPLE", sampleId.toString(), Instant.now(),
                "{\"status\":\"ANALYSIS_DRAFT\"}", "{\"status\":\"SUBMITTED\"}");
    }

    public SampleDto createRevision(Session session, UUID originalSampleId, String newSampleCode) {
        if (!session.isAnalystOrAdmin()) {
            throw new AuthorizationException("Only analysts or administrators can create sample revisions.");
        }
        WaterSample original = sampleRepository.findById(originalSampleId)
                .orElseThrow(() -> new ValidationException("Original sample not found: " + originalSampleId));

        if (original.getStatus() != SampleStatus.SUBMITTED && original.getStatus() != SampleStatus.EVALUATED) {
            throw new ValidationException("Revisions can only be created for submitted or evaluated samples.");
        }

        String normalizedCode = newSampleCode.trim().toUpperCase();
        if (sampleRepository.findByCode(normalizedCode).isPresent()) {
            throw new ValidationException("Sample code '" + normalizedCode + "' already exists.");
        }

        UUID newSampleId = UUID.randomUUID();
        Instant now = Instant.now();
        WaterSample revision = new WaterSample(
                newSampleId,
                normalizedCode,
                original.getInspectionId(),
                SampleStatus.ANALYSIS_DRAFT,
                original.getCollectedAt(),
                original.getReceivedAt(),
                original.getCollectedBy(),
                original.getOrigin(),
                original.getImportBatchId(),
                original.getId(),
                now,
                1
        );

        sampleRepository.save(revision);

        // Copy original readings into revision draft
        List<ParameterReading> origReadings = readingRepository.findBySampleId(originalSampleId);
        for (ParameterReading r : origReadings) {
            ParameterReading copy = new ParameterReading(
                    UUID.randomUUID(),
                    newSampleId,
                    r.getParameterId(),
                    r.getValueDecimal(),
                    r.getUnit(),
                    session.userId(),
                    now,
                    r.getProvenanceKind(),
                    null
            );
            readingRepository.save(copy);
        }

        auditRepository.record(session.userId(), "SAMPLE_REVISION_CREATED", "SAMPLE", newSampleId.toString(), now,
                "{\"supersedes\":\"" + original.getId() + "\"}", "{\"newSampleId\":\"" + newSampleId + "\"}");

        return SampleDto.from(revision);
    }

    public List<ReadingDto> getReadings(Session session, UUID sampleId) {
        Map<UUID, String> paramCodeMap = parameterRepository.findAll().stream()
                .collect(Collectors.toMap(ParameterDefinition::getId, ParameterDefinition::getCode));

        return readingRepository.findBySampleId(sampleId).stream()
                .map(r -> ReadingDto.of(r, paramCodeMap.getOrDefault(r.getParameterId(), "UNKNOWN")))
                .toList();
    }

    public Optional<SampleDto> getSample(Session session, UUID id) {
        return sampleRepository.findById(id).map(SampleDto::from);
    }

    public List<SampleDto> listSamples(Session session, int offset, int limit) {
        return sampleRepository.findAll(offset, limit).stream().map(SampleDto::from).toList();
    }

    private void validateReadingValue(ParameterDefinition param, BigDecimal value) {
        if (value == null) {
            throw new ValidationException("Reading value cannot be null.");
        }
        if (param.getInputMinDecimal() != null && value.compareTo(param.getInputMinDecimal()) < 0) {
            throw new ValidationException("Value " + value + " for parameter " + param.getCode() +
                    " is below physical constraint minimum of " + param.getInputMinDecimal());
        }
        if (param.getInputMaxDecimal() != null && value.compareTo(param.getInputMaxDecimal()) > 0) {
            throw new ValidationException("Value " + value + " for parameter " + param.getCode() +
                    " exceeds physical constraint maximum of " + param.getInputMaxDecimal());
        }
    }
}
