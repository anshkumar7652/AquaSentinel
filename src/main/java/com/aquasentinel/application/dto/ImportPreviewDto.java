package com.aquasentinel.application.dto;

import com.aquasentinel.domain.enums.ImportRecordKind;
import com.aquasentinel.domain.enums.RecordOrigin;
import com.aquasentinel.domain.enums.SummaryStatistic;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ImportPreviewDto(
        UUID previewId,
        String filename,
        String fileSha256,
        int totalRows,
        int pointSampleCount,
        int periodSummaryCount,
        List<String> validationErrors,
        boolean validToCommit,
        List<ParsedImportRow> sampleRows
) {
    public record ParsedImportRow(
            int rowNumber,
            String sourceCode,
            String recordCode,
            ImportRecordKind recordKind,
            Instant observedAt,
            LocalDate periodStart,
            LocalDate periodEnd,
            SummaryStatistic statistic,
            String parameterCode,
            BigDecimal value,
            String unit,
            String rowError
    ) {}

    public record ImportMetadata(
            String publisher,
            String datasetTitle,
            String sourceUrl,
            String accessedOn,
            String licenseStatus,
            String transformations,
            RecordOrigin provenanceKind
    ) {}

    public record ImportReceipt(
            UUID batchId,
            String fileSha256,
            int rowsImported,
            int samplesCreated,
            int summariesSaved
    ) {}
}
