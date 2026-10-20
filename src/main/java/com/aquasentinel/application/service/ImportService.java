package com.aquasentinel.application.service;

import com.aquasentinel.application.dto.ImportPreviewDto;
import com.aquasentinel.application.dto.ImportPreviewDto.ImportMetadata;
import com.aquasentinel.application.dto.ImportPreviewDto.ImportReceipt;
import com.aquasentinel.application.dto.ImportPreviewDto.ParsedImportRow;
import com.aquasentinel.application.port.AuditRepository;
import com.aquasentinel.application.port.ImportBatchRepository;
import com.aquasentinel.application.port.InspectionRepository;
import com.aquasentinel.application.port.ParameterRepository;
import com.aquasentinel.application.port.ReadingRepository;
import com.aquasentinel.application.port.SampleRepository;
import com.aquasentinel.application.port.SourceRepository;
import com.aquasentinel.application.security.Session;
import com.aquasentinel.domain.enums.ImportRecordKind;
import com.aquasentinel.domain.enums.InspectionStatus;
import com.aquasentinel.domain.enums.RecordOrigin;
import com.aquasentinel.domain.enums.SampleStatus;
import com.aquasentinel.domain.enums.SummaryStatistic;
import com.aquasentinel.domain.exception.AuthorizationException;
import com.aquasentinel.domain.exception.ValidationException;
import com.aquasentinel.domain.model.HistoricalObservation;
import com.aquasentinel.domain.model.ImportBatch;
import com.aquasentinel.domain.model.Inspection;
import com.aquasentinel.domain.model.ParameterDefinition;
import com.aquasentinel.domain.model.ParameterReading;
import com.aquasentinel.domain.model.WaterSample;
import com.aquasentinel.domain.model.WaterSource;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ImportService {
    private final ImportBatchRepository importBatchRepository;
    private final SourceRepository sourceRepository;
    private final ParameterRepository parameterRepository;
    private final InspectionRepository inspectionRepository;
    private final SampleRepository sampleRepository;
    private final ReadingRepository readingRepository;
    private final AuditRepository auditRepository;

    // In-memory preview staging cache for active session previews
    private final Map<UUID, StagedPreview> stagedPreviews = new ConcurrentHashMap<>();

    private record StagedPreview(
            ImportPreviewDto previewDto,
            ImportMetadata metadata,
            Path sourceFile,
            List<ParsedImportRow> validRows
    ) {}

    public ImportService(ImportBatchRepository importBatchRepository,
                         SourceRepository sourceRepository,
                         ParameterRepository parameterRepository,
                         InspectionRepository inspectionRepository,
                         SampleRepository sampleRepository,
                         ReadingRepository readingRepository,
                         AuditRepository auditRepository) {
        this.importBatchRepository = importBatchRepository;
        this.sourceRepository = sourceRepository;
        this.parameterRepository = parameterRepository;
        this.inspectionRepository = inspectionRepository;
        this.sampleRepository = sampleRepository;
        this.readingRepository = readingRepository;
        this.auditRepository = auditRepository;
    }

    public ImportPreviewDto preview(Session session, Path csvFile, ImportMetadata metadata) {
        if (!session.isAnalystOrAdmin()) {
            throw new AuthorizationException("Only analysts or administrators can import water datasets.");
        }
        if (!Files.exists(csvFile)) {
            throw new ValidationException("Import file does not exist: " + csvFile);
        }

        String fileHash;
        try {
            fileHash = computeFileSha256(csvFile);
        } catch (Exception e) {
            throw new ValidationException("Failed to calculate SHA-256 for file: " + e.getMessage());
        }

        if (importBatchRepository.findByFileSha256(fileHash).isPresent()) {
            throw new ValidationException("Duplicate dataset detected: file with identical content has already been committed.");
        }

        List<String> errors = new ArrayList<>();
        List<ParsedImportRow> parsedRows = new ArrayList<>();
        int pointCount = 0;
        int summaryCount = 0;

        Map<String, WaterSource> sourceCache = new HashMap<>();
        Map<String, ParameterDefinition> paramCache = new HashMap<>();

        try (InputStream in = Files.newInputStream(csvFile);
             InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8);
             CSVParser parser = CSVFormat.DEFAULT.builder()
                     .setHeader()
                     .setSkipHeaderRecord(true)
                     .setIgnoreSurroundingSpaces(true)
                     .build().parse(reader)) {

            int rowNum = 1;
            for (CSVRecord record : parser) {
                rowNum++;
                String rowError = null;

                String sourceCode = getField(record, "source_code");
                String recordCode = getField(record, "record_code");
                String kindStr = getField(record, "record_kind");
                String obsStr = getField(record, "observed_at");
                String startStr = getField(record, "period_start");
                String endStr = getField(record, "period_end");
                String statStr = getField(record, "statistic");
                String paramCode = getField(record, "parameter_code");
                String valStr = getField(record, "value");
                String unit = getField(record, "unit");

                if (sourceCode == null || paramCode == null || valStr == null || kindStr == null) {
                    rowError = "Missing required column(s) on row " + rowNum;
                }

                ImportRecordKind recordKind = null;
                if (rowError == null) {
                    try {
                        recordKind = ImportRecordKind.valueOf(kindStr.toUpperCase());
                    } catch (Exception e) {
                        rowError = "Invalid record_kind '" + kindStr + "' on row " + rowNum;
                    }
                }

                // Check source exists
                if (rowError == null && !sourceCache.containsKey(sourceCode.toUpperCase())) {
                    Optional<WaterSource> srcOpt = sourceRepository.findByCode(sourceCode.toUpperCase());
                    if (srcOpt.isEmpty()) {
                        rowError = "Unknown water source code '" + sourceCode + "' on row " + rowNum;
                    } else {
                        sourceCache.put(sourceCode.toUpperCase(), srcOpt.get());
                    }
                }

                // Check parameter exists
                if (rowError == null && !paramCache.containsKey(paramCode.toUpperCase())) {
                    Optional<ParameterDefinition> pOpt = parameterRepository.findByCode(paramCode.toUpperCase());
                    if (pOpt.isEmpty()) {
                        rowError = "Unknown parameter code '" + paramCode + "' on row " + rowNum;
                    } else {
                        paramCache.put(paramCode.toUpperCase(), pOpt.get());
                    }
                }

                BigDecimal val = null;
                if (rowError == null) {
                    try {
                        val = new BigDecimal(valStr.trim());
                    } catch (Exception e) {
                        rowError = "Invalid numeric decimal value '" + valStr + "' on row " + rowNum;
                    }
                }

                Instant obsAt = null;
                LocalDate pStart = null;
                LocalDate pEnd = null;
                SummaryStatistic stat = null;

                if (rowError == null) {
                    if (recordKind == ImportRecordKind.POINT_SAMPLE) {
                        pointCount++;
                        if (obsStr == null || obsStr.isBlank()) {
                            rowError = "POINT_SAMPLE requires observed_at timestamp on row " + rowNum;
                        } else {
                            try {
                                obsAt = Instant.parse(obsStr.trim());
                            } catch (Exception e) {
                                rowError = "Invalid ISO-8601 observed_at on row " + rowNum;
                            }
                        }
                    } else if (recordKind == ImportRecordKind.PERIOD_SUMMARY) {
                        summaryCount++;
                        if (startStr == null || endStr == null || statStr == null) {
                            rowError = "PERIOD_SUMMARY requires period_start, period_end, and statistic on row " + rowNum;
                        } else {
                            try {
                                pStart = LocalDate.parse(startStr.trim());
                                pEnd = LocalDate.parse(endStr.trim());
                                stat = SummaryStatistic.valueOf(statStr.trim().toUpperCase());
                                if (pStart.isAfter(pEnd)) {
                                    rowError = "period_start cannot follow period_end on row " + rowNum;
                                }
                            } catch (Exception e) {
                                rowError = "Invalid period date format or statistic on row " + rowNum;
                            }
                        }
                    }
                }

                if (rowError != null) {
                    errors.add(rowError);
                }

                parsedRows.add(new ParsedImportRow(
                        rowNum, sourceCode, recordCode, recordKind, obsAt,
                        pStart, pEnd, stat, paramCode, val, unit, rowError
                ));
            }
        } catch (Exception e) {
            throw new ValidationException("Failed to parse CSV file: " + e.getMessage());
        }

        UUID previewId = UUID.randomUUID();
        boolean validToCommit = errors.isEmpty() && !parsedRows.isEmpty();

        ImportPreviewDto previewDto = new ImportPreviewDto(
                previewId,
                csvFile.getFileName().toString(),
                fileHash,
                parsedRows.size(),
                pointCount,
                summaryCount,
                errors,
                validToCommit,
                parsedRows
        );

        if (validToCommit) {
            stagedPreviews.put(previewId, new StagedPreview(previewDto, metadata, csvFile, parsedRows));
        }

        return previewDto;
    }

    public ImportReceipt commit(Session session, UUID previewId) {
        if (!session.isAnalystOrAdmin()) {
            throw new AuthorizationException("Only analysts or administrators can commit imported data.");
        }

        StagedPreview staged = stagedPreviews.remove(previewId);
        if (staged == null) {
            throw new ValidationException("Staged preview has expired or was cancelled.");
        }

        // Re-check hash uniqueness
        if (importBatchRepository.findByFileSha256(staged.previewDto().fileSha256()).isPresent()) {
            throw new ValidationException("This dataset batch has already been committed.");
        }

        UUID batchId = UUID.randomUUID();
        Instant now = Instant.now();
        ImportMetadata meta = staged.metadata();

        ImportBatch batch = new ImportBatch(
                batchId,
                staged.previewDto().fileSha256(),
                staged.previewDto().filename(),
                meta.publisher(),
                meta.datasetTitle(),
                meta.sourceUrl(),
                meta.accessedOn(),
                meta.licenseStatus(),
                meta.transformations(),
                meta.provenanceKind() != null ? meta.provenanceKind() : RecordOrigin.HISTORICAL_IMPORT,
                session.userId(),
                now,
                staged.validRows().size()
        );

        importBatchRepository.save(batch);

        int samplesCreated = 0;
        int summariesSaved = 0;
        List<HistoricalObservation> summaries = new ArrayList<>();

        // Group POINT_SAMPLE rows by (source_code, record_code)
        Map<String, List<ParsedImportRow>> sampleGroups = new HashMap<>();

        for (ParsedImportRow row : staged.validRows()) {
            if (row.recordKind() == ImportRecordKind.PERIOD_SUMMARY) {
                WaterSource src = sourceRepository.findByCode(row.sourceCode().toUpperCase()).orElseThrow();
                ParameterDefinition param = parameterRepository.findByCode(row.parameterCode().toUpperCase()).orElseThrow();

                HistoricalObservation obs = new HistoricalObservation(
                        UUID.randomUUID(),
                        src.getId(),
                        param.getId(),
                        row.periodStart(),
                        row.periodEnd(),
                        row.statistic(),
                        row.value(),
                        row.unit() != null ? row.unit() : param.getCanonicalUnit(),
                        batchId,
                        row.rowNumber()
                );
                summaries.add(obs);
                summariesSaved++;
            } else if (row.recordKind() == ImportRecordKind.POINT_SAMPLE) {
                String key = row.sourceCode().toUpperCase() + "::" + (row.recordCode() != null ? row.recordCode().toUpperCase() : "AUTO");
                sampleGroups.computeIfAbsent(key, k -> new ArrayList<>()).add(row);
            }
        }

        if (!summaries.isEmpty()) {
            importBatchRepository.saveObservations(summaries);
        }

        // Create Inspection, WaterSample, and ParameterReadings for point samples
        for (Map.Entry<String, List<ParsedImportRow>> entry : sampleGroups.entrySet()) {
            List<ParsedImportRow> groupRows = entry.getValue();
            ParsedImportRow first = groupRows.get(0);
            WaterSource src = sourceRepository.findByCode(first.sourceCode().toUpperCase()).orElseThrow();

            UUID inspectionId = UUID.randomUUID();
            Inspection inspection = new Inspection(
                    inspectionId,
                    src.getId(),
                    session.userId(),
                    LocalDate.now(),
                    InspectionStatus.COMPLETED,
                    null,
                    "Imported from external dataset batch: " + batch.getDatasetTitle(),
                    now,
                    1
            );
            inspectionRepository.save(inspection);

            String sampleCode = "IMP-" + (first.recordCode() != null ? first.recordCode().toUpperCase() : UUID.randomUUID().toString().substring(0, 8).toUpperCase());
            UUID sampleId = UUID.randomUUID();

            WaterSample sample = new WaterSample(
                    sampleId,
                    sampleCode,
                    inspectionId,
                    SampleStatus.ANALYSIS_DRAFT,
                    first.observedAt(),
                    now,
                    session.userId(),
                    RecordOrigin.HISTORICAL_IMPORT,
                    batchId,
                    null,
                    now,
                    1
            );
            sampleRepository.save(sample);
            samplesCreated++;

            for (ParsedImportRow r : groupRows) {
                ParameterDefinition p = parameterRepository.findByCode(r.parameterCode().toUpperCase()).orElseThrow();
                ParameterReading reading = new ParameterReading(
                        UUID.randomUUID(),
                        sampleId,
                        p.getId(),
                        r.value(),
                        r.unit() != null ? r.unit() : p.getCanonicalUnit(),
                        session.userId(),
                        now,
                        RecordOrigin.HISTORICAL_IMPORT,
                        r.rowNumber()
                );
                readingRepository.save(reading);
            }
        }

        auditRepository.record(session.userId(), "DATASET_COMMITTED", "IMPORT_BATCH", batchId.toString(), now,
                null, "{\"filename\":\"" + batch.getFilename() + "\",\"rows\":" + batch.getRowCount() + "}");

        return new ImportReceipt(batchId, batch.getFileSha256(), batch.getRowCount(), samplesCreated, summariesSaved);
    }

    private String getField(CSVRecord record, String name) {
        if (record.isMapped(name)) {
            String val = record.get(name);
            return val != null ? val.trim() : null;
        }
        return null;
    }

    private String computeFileSha256(Path file) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buf = new byte[8192];
            int read;
            while ((read = in.read(buf)) != -1) {
                md.update(buf, 0, read);
            }
        }
        return HexFormat.of().formatHex(md.digest());
    }
}
