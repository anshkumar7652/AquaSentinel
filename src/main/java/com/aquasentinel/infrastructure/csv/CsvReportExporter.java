package com.aquasentinel.infrastructure.csv;

import com.aquasentinel.application.dto.ReportSnapshot;
import com.aquasentinel.application.port.ReportExporter;
import com.aquasentinel.domain.evaluation.RuleEvaluationResult;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

public class CsvReportExporter implements ReportExporter {

    @Override
    public void export(ReportSnapshot snapshot, OutputStream outputStream) {
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setCommentMarker('#')
                .build();
        try (OutputStreamWriter writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8);
             CSVPrinter printer = new CSVPrinter(writer, format)) {

            // Header Comments / Metadata
            printer.printComment("AquaSentinel Water Quality Profile Evaluation Report");
            printer.printComment("Generated: " + snapshot.generatedAt());
            printer.printComment("Disclaimer: " + escapeFormula(snapshot.disclaimer()));
            printer.printComment("Source: " + escapeFormula(snapshot.sourceCode()) + " (" + escapeFormula(snapshot.sourceName()) + ")");
            printer.printComment("Sample: " + escapeFormula(snapshot.sampleCode()) + " | Collected: " + snapshot.collectedAt());
            printer.printComment("Profile: " + escapeFormula(snapshot.profileName()) + " v" + snapshot.profileVersion());
            printer.printComment("Overall Outcome: " + snapshot.outcome().name());
            printer.printComment("Input Digest: " + snapshot.inputDigest());

            // Column Header
            printer.printRecord(
                    "PARAMETER",
                    "RESULT",
                    "OBSERVED_VALUE",
                    "UNIT",
                    "LOWER_LIMIT",
                    "UPPER_LIMIT",
                    "REQUIRED",
                    "EXPLANATION"
            );

            // Data Rows
            for (RuleEvaluationResult r : snapshot.ruleResults()) {
                printer.printRecord(
                        escapeFormula(r.parameterCode()),
                        r.outcome().name(),
                        r.observedValue() != null ? r.observedValue().toPlainString() : "N/A",
                        escapeFormula(r.observedUnit() != null ? r.observedUnit() : ""),
                        r.lowerLimit() != null ? r.lowerLimit().toPlainString() : "",
                        r.upperLimit() != null ? r.upperLimit().toPlainString() : "",
                        r.required() ? "YES" : "NO",
                        escapeFormula(r.explanation())
                );
            }

            printer.flush();
        } catch (IOException e) {
            throw new RuntimeException("Failed to generate CSV evaluation report", e);
        }
    }

    @Override
    public String getContentType() {
        return "text/csv";
    }

    @Override
    public String getFileExtension() {
        return "csv";
    }

    /**
     * Prevents CSV formula injection vulnerability by prefixing '=', '+', '-', or '@' with single quote.
     */
    private String escapeFormula(String value) {
        if (value == null) return "";
        if (value.startsWith("=") || value.startsWith("+") || value.startsWith("-") || value.startsWith("@")) {
            return "'" + value;
        }
        return value;
    }
}
