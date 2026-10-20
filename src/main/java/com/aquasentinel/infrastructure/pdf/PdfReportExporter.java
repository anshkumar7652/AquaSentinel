package com.aquasentinel.infrastructure.pdf;

import com.aquasentinel.application.dto.ReportSnapshot;
import com.aquasentinel.application.port.ReportExporter;
import com.aquasentinel.domain.evaluation.RuleEvaluationResult;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import java.io.IOException;
import java.io.OutputStream;

public class PdfReportExporter implements ReportExporter {

    @Override
    public void export(ReportSnapshot snapshot, OutputStream outputStream) {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);

            PDFont fontBold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDFont fontRegular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDFont fontOblique = new PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE);

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                float margin = 40;
                float y = page.getMediaBox().getHeight() - margin;

                // Title
                cs.beginText();
                cs.setFont(fontBold, 18);
                cs.newLineAtOffset(margin, y);
                cs.showText("AquaSentinel — Water Quality Evaluation Report");
                cs.endText();

                y -= 25;
                // Subtitle / Disclaimer banner
                cs.beginText();
                cs.setFont(fontOblique, 9);
                cs.newLineAtOffset(margin, y);
                cs.showText(snapshot.disclaimer());
                cs.endText();

                y -= 25;
                // Metadata block
                cs.beginText();
                cs.setFont(fontBold, 10);
                cs.newLineAtOffset(margin, y);
                cs.showText("Source: ");
                cs.setFont(fontRegular, 10);
                cs.showText(snapshot.sourceCode() + " - " + snapshot.sourceName() + " (" + snapshot.sourceType() + ")");
                cs.endText();

                y -= 15;
                cs.beginText();
                cs.setFont(fontBold, 10);
                cs.newLineAtOffset(margin, y);
                cs.showText("Location: ");
                cs.setFont(fontRegular, 10);
                cs.showText(snapshot.locationText() != null ? snapshot.locationText() : "N/A");
                cs.endText();

                y -= 15;
                cs.beginText();
                cs.setFont(fontBold, 10);
                cs.newLineAtOffset(margin, y);
                cs.showText("Sample Code: ");
                cs.setFont(fontRegular, 10);
                cs.showText(snapshot.sampleCode() + " | Collected: " + snapshot.collectedAt() + " | Provenance: " + snapshot.provenance());
                cs.endText();

                y -= 15;
                cs.beginText();
                cs.setFont(fontBold, 10);
                cs.newLineAtOffset(margin, y);
                cs.showText("Evaluation Profile: ");
                cs.setFont(fontRegular, 10);
                cs.showText(snapshot.profileName() + " (v" + snapshot.profileVersion() + ")");
                cs.endText();

                y -= 15;
                cs.beginText();
                cs.setFont(fontBold, 10);
                cs.newLineAtOffset(margin, y);
                cs.showText("Overall Outcome: ");
                cs.setFont(fontBold, 11);
                cs.showText(snapshot.outcome().name());
                cs.setFont(fontRegular, 9);
                cs.showText("  (Evaluated at: " + snapshot.evaluatedAt() + " by " + snapshot.evaluatedByUsername() + ")");
                cs.endText();

                y -= 15;
                cs.beginText();
                cs.setFont(fontBold, 9);
                cs.newLineAtOffset(margin, y);
                cs.showText("Input Digest (SHA-256): ");
                cs.setFont(fontRegular, 8);
                cs.showText(snapshot.inputDigest());
                cs.endText();

                // Table divider line
                y -= 20;
                cs.moveTo(margin, y);
                cs.lineTo(page.getMediaBox().getWidth() - margin, y);
                cs.stroke();

                // Table Header
                y -= 15;
                cs.beginText();
                cs.setFont(fontBold, 9);
                cs.newLineAtOffset(margin, y);
                cs.showText(String.format("%-12s %-12s %-15s %-18s %-10s %s",
                        "PARAMETER", "RESULT", "OBSERVED", "RULE THRESHOLD", "REQUIRED", "EXPLANATION"));
                cs.endText();

                y -= 8;
                cs.moveTo(margin, y);
                cs.lineTo(page.getMediaBox().getWidth() - margin, y);
                cs.stroke();

                // Rows
                for (RuleEvaluationResult r : snapshot.ruleResults()) {
                    y -= 16;
                    String obs = (r.observedValue() != null ? r.observedValue().toPlainString() : "N/A") + " " + (r.observedUnit() != null ? r.observedUnit() : "");
                    String thresh = (r.lowerLimit() != null ? r.lowerLimit().toPlainString() : "") +
                            " - " + (r.upperLimit() != null ? r.upperLimit().toPlainString() : "");
                    String req = r.required() ? "Yes" : "No";

                    cs.beginText();
                    cs.setFont(fontRegular, 8);
                    cs.newLineAtOffset(margin, y);
                    cs.showText(String.format("%-12s %-12s %-15s %-18s %-10s %s",
                            r.parameterCode(),
                            r.outcome().name(),
                            obs,
                            thresh,
                            req,
                            r.explanation() != null ? r.explanation() : ""));
                    cs.endText();
                }

                // Footer
                cs.beginText();
                cs.setFont(fontRegular, 8);
                cs.newLineAtOffset(margin, margin);
                cs.showText("Generated by AquaSentinel Platform | SDG 6 Clean Water & Sanitation Monitoring System");
                cs.endText();
            }

            doc.save(outputStream);
        } catch (IOException e) {
            throw new RuntimeException("Failed to generate PDF report", e);
        }
    }

    @Override
    public String getContentType() {
        return "application/pdf";
    }

    @Override
    public String getFileExtension() {
        return "pdf";
    }
}
