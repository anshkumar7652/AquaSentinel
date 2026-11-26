package com.aquasentinel.application.service;

import com.aquasentinel.app.AppBootstrap;
import com.aquasentinel.app.AppContext;
import com.aquasentinel.application.dto.*;
import com.aquasentinel.application.dto.ImportPreviewDto.ImportMetadata;
import com.aquasentinel.application.dto.ImportPreviewDto.ImportReceipt;
import com.aquasentinel.application.security.Session;
import com.aquasentinel.domain.enums.*;
import com.aquasentinel.domain.model.ProfileVersion;
import com.aquasentinel.infrastructure.csv.CsvReportExporter;
import com.aquasentinel.infrastructure.pdf.PdfReportExporter;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class FullSystemEndToEndTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("E2E Test: Full User Journey - Bootstrap, Seeding, CSV Import, Lifecycle, Evaluation, Alert Resolution, and Reports")
    void testCompleteSystemJourney() throws Exception {
        // 1. Bootstrap Application in isolated test sandbox
        Path dbPath = tempDir.resolve("e2e-aquasentinel.db");
        AppContext context = new AppContext(tempDir, dbPath, true);
        AppBootstrap bootstrap = new AppBootstrap(context);
        bootstrap.initialize();

        assertTrue(Files.exists(context.databasePath()), "Database file should exist after bootstrap");

        Session adminSession = bootstrap.getOrCreateAdminSession();
        assertNotNull(adminSession);
        assertEquals(Role.ADMINISTRATOR, adminSession.role());

        // 2. Verify auto-seeded demonstration dataset
        List<SourceDto> sources = bootstrap.getSourceService().listSources(adminSession, 0, 100);
        assertFalse(sources.isEmpty(), "Demo sources should have been seeded");
        SourceDto s1 = sources.getFirst();

        // 3. Import Delhi Monitoring Demo CSV
        Path demoCsvPath = Path.of("data", "demo", "delhi_monitoring_demo.csv");
        assertTrue(Files.exists(demoCsvPath), "Demo CSV fixture must exist at data/demo/delhi_monitoring_demo.csv");

        ImportMetadata meta = new ImportMetadata(
                "Central Water Commission", "Delhi Water Monitoring", "https://cpcb.nic.in",
                "2026-10-20", "Open Access", "Verified educational demo batch", RecordOrigin.HISTORICAL_IMPORT
        );
        ImportPreviewDto preview = bootstrap.getImportService().preview(adminSession, demoCsvPath, meta);
        assertNotNull(preview, "CSV preview should parse successfully");
        assertTrue(preview.validationErrors().isEmpty(), "Demo CSV should have zero validation errors: " + preview.validationErrors());
        assertTrue(preview.validToCommit(), "Demo CSV must be valid to commit");

        ImportReceipt receipt = bootstrap.getImportService().commit(adminSession, preview.previewId());
        assertNotNull(receipt);
        assertTrue(receipt.rowsImported() > 0, "Receipt must show imported rows");

        // 4. Test Complete Sample Lifecycle with Out-of-Spec Values
        InspectionDto inspection = bootstrap.getInspectionService().scheduleInspection(
                adminSession, s1.id(), adminSession.userId(), LocalDate.now(), "E2E Comprehensive Water Testing"
        );
        assertNotNull(inspection);

        SampleDto sample = bootstrap.getSampleService().registerSample(
                adminSession, "SMP-E2E-TEST-001", inspection.id(), RecordOrigin.MANUAL_ENTRY
        );
        assertEquals(SampleStatus.REGISTERED, sample.status());

        Instant collectionTime = Instant.now().minusSeconds(7200);
        bootstrap.getSampleService().recordCollection(adminSession, sample.id(), collectionTime, adminSession.userId());
        bootstrap.getSampleService().recordReceipt(adminSession, sample.id(), collectionTime.plusSeconds(600));

        // Draft readings: intentionally high Turbidity (14.5 exceeds 5.0 max in surface water demo)
        bootstrap.getSampleService().enterDraftReadings(adminSession, sample.id(), Map.of(
                "PH", new BigDecimal("7.10"),
                "TURB", new BigDecimal("14.50"),
                "DO", new BigDecimal("5.50"),
                "TDS", new BigDecimal("410.00")
        ));

        bootstrap.getSampleService().submitSample(adminSession, sample.id());
        SampleDto submittedSample = bootstrap.getSampleService().getSample(adminSession, sample.id()).orElseThrow();
        assertEquals(SampleStatus.SUBMITTED, submittedSample.status());

        // 5. Evaluate against Profile and Verify Alert Creation
        ProfileVersion profile = bootstrap.getProfileRepository().findByKeyAndVersion("SURFACE_WATER_DEMO", 1).orElseThrow();
        EvaluationDto eval = bootstrap.getEvaluationService().evaluate(adminSession, sample.id(), profile.getId());
        assertNotNull(eval);
        assertEquals(EvaluationOutcome.EXCEEDS_PROFILE, eval.outcome());
        assertNotNull(eval.inputDigest(), "Input digest must be computed");

        List<AlertDto> openAlerts = bootstrap.getActionService().getAlerts(AlertState.OPEN);
        assertFalse(openAlerts.isEmpty(), "Open alerts must exist for exceeding sample");
        AlertDto alert = openAlerts.stream().filter(a -> a.sampleId().equals(sample.id())).findFirst().orElseThrow();
        assertEquals(AlertState.OPEN, alert.state());

        // 6. Action Lifecycle & Verified Retest Auto-Resolution
        AlertDto ackedAlert = bootstrap.getActionService().acknowledgeAlert(adminSession, alert.id());
        assertEquals(AlertState.ACKNOWLEDGED, ackedAlert.state());

        ActionDto action = bootstrap.getActionService().createAction(
                adminSession, alert.id(), adminSession.userId(),
                "Investigate high turbidity runoff and flush filter beds", LocalDate.now().plusDays(5)
        );
        assertEquals(ActionState.OPEN, action.state());

        action = bootstrap.getActionService().updateState(adminSession, action.id(), ActionState.IN_PROGRESS);
        assertEquals(ActionState.IN_PROGRESS, action.state());

        action = bootstrap.getActionService().updateState(adminSession, action.id(), ActionState.AWAITING_RETEST);
        assertEquals(ActionState.AWAITING_RETEST, action.state());

        // Register and evaluate passing retest sample (subsequent collection timestamp at same source)
        Instant retestCollectionTime = collectionTime.plusSeconds(3600);
        SampleDto retestSample = bootstrap.getSampleService().registerSample(adminSession, "SMP-E2E-RETEST-002", inspection.id(), RecordOrigin.MANUAL_ENTRY);
        bootstrap.getSampleService().recordCollection(adminSession, retestSample.id(), retestCollectionTime, adminSession.userId());
        bootstrap.getSampleService().recordReceipt(adminSession, retestSample.id(), retestCollectionTime.plusSeconds(300));
        bootstrap.getSampleService().enterDraftReadings(adminSession, retestSample.id(), Map.of(
                "PH", new BigDecimal("7.30"),
                "TURB", new BigDecimal("2.10"),
                "DO", new BigDecimal("6.80"),
                "TDS", new BigDecimal("320.00")
        ));
        bootstrap.getSampleService().submitSample(adminSession, retestSample.id());
        EvaluationDto retestEval = bootstrap.getEvaluationService().evaluate(adminSession, retestSample.id(), profile.getId());
        assertEquals(EvaluationOutcome.WITHIN_PROFILE, retestEval.outcome());

        // Close action with verified retest
        action = bootstrap.getActionService().closeAction(adminSession, action.id(), "Retest passed with Turbidity 2.1 NTU", retestEval.id());
        assertEquals(ActionState.CLOSED, action.state());

        // Verify alert auto-resolved
        var resolvedAlert = bootstrap.getAlertRepository().findById(alert.id()).orElseThrow();
        assertEquals(AlertState.RESOLVED, resolvedAlert.state());

        // 7. Verify Dashboard KPIs
        DashboardDto.KpiSummary kpis = bootstrap.getDashboardService().getKpiSummary();
        assertNotNull(kpis);
        assertTrue(kpis.samplesInPeriodCount() >= 2);
        assertTrue(kpis.activeSourcesCount() >= 1);

        // 8. Generate and Verify Reports (PDF & CSV)
        ByteArrayOutputStream csvOut = new ByteArrayOutputStream();
        bootstrap.getReportService().exportEvaluationReport(adminSession, eval.id(), new CsvReportExporter(), csvOut);
        byte[] csvBytes = csvOut.toByteArray();
        assertTrue(csvBytes.length > 0, "CSV bytes should not be empty");
        String csvString = new String(csvBytes, StandardCharsets.UTF_8);
        assertTrue(csvString.contains("AquaSentinel"), "CSV report should contain application header");

        ByteArrayOutputStream pdfOut = new ByteArrayOutputStream();
        bootstrap.getReportService().exportEvaluationReport(adminSession, eval.id(), new PdfReportExporter(), pdfOut);
        byte[] pdfBytes = pdfOut.toByteArray();
        assertTrue(pdfBytes.length > 500, "PDF should contain valid formatted binary structure");
        try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
            assertTrue(doc.getNumberOfPages() >= 1, "PDF must have at least 1 page");
        }
    }

    @Test
    @DisplayName("Verify FXML Documents Structure and Well-Formedness")
    void testAllFxmlDocumentsValid() throws Exception {
        String[] fxmlFiles = {
                "/fxml/admin.fxml",
                "/fxml/alerts.fxml",
                "/fxml/app-shell.fxml",
                "/fxml/dashboard.fxml",
                "/fxml/imports.fxml",
                "/fxml/inspections.fxml",
                "/fxml/reports.fxml",
                "/fxml/samples.fxml",
                "/fxml/sources.fxml"
        };

        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        DocumentBuilder builder = dbf.newDocumentBuilder();

        for (String fxmlPath : fxmlFiles) {
            try (InputStream is = getClass().getResourceAsStream(fxmlPath)) {
                assertNotNull(is, "FXML resource must be present: " + fxmlPath);
                Document doc = builder.parse(is);
                assertNotNull(doc.getDocumentElement(), "FXML root element must not be null for: " + fxmlPath);
            }
        }
    }
}
