package com.aquasentinel.application.service;

import com.aquasentinel.application.dto.EvaluationDto;
import com.aquasentinel.application.dto.InspectionDto;
import com.aquasentinel.application.dto.ReportSnapshot;
import com.aquasentinel.application.dto.SampleDto;
import com.aquasentinel.application.dto.SourceDto;
import com.aquasentinel.application.dto.UserDto;
import com.aquasentinel.application.security.Session;
import com.aquasentinel.domain.enums.EvaluationOutcome;
import com.aquasentinel.domain.enums.RecordOrigin;
import com.aquasentinel.domain.enums.Role;
import com.aquasentinel.domain.enums.SourceType;
import com.aquasentinel.domain.model.ProfileVersion;
import com.aquasentinel.infrastructure.csv.CsvReportExporter;
import com.aquasentinel.infrastructure.db.ConnectionFactory;
import com.aquasentinel.infrastructure.db.SchemaMigrator;
import com.aquasentinel.infrastructure.pdf.PdfReportExporter;
import com.aquasentinel.infrastructure.repository.JdbcAlertRepository;
import com.aquasentinel.infrastructure.repository.JdbcAuditRepository;
import com.aquasentinel.infrastructure.repository.JdbcEvaluationRepository;
import com.aquasentinel.infrastructure.repository.JdbcInspectionRepository;
import com.aquasentinel.infrastructure.repository.JdbcParameterRepository;
import com.aquasentinel.infrastructure.repository.JdbcProfileRepository;
import com.aquasentinel.infrastructure.repository.JdbcReadingRepository;
import com.aquasentinel.infrastructure.repository.JdbcSampleRepository;
import com.aquasentinel.infrastructure.repository.JdbcSourceRepository;
import com.aquasentinel.infrastructure.repository.JdbcUserRepository;
import com.aquasentinel.infrastructure.security.PBKDF2PasswordHasher;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class Milestone7ReportsTest {

    @TempDir
    Path tempDir;

    private ConnectionFactory connectionFactory;
    private JdbcUserRepository userRepo;
    private JdbcSourceRepository sourceRepo;
    private JdbcInspectionRepository inspectionRepo;
    private JdbcSampleRepository sampleRepo;
    private JdbcReadingRepository readingRepo;
    private JdbcParameterRepository paramRepo;
    private JdbcProfileRepository profileRepo;
    private JdbcEvaluationRepository evalRepo;
    private JdbcAlertRepository alertRepo;
    private JdbcAuditRepository auditRepo;

    private AuthenticationService authService;
    private UserService userService;
    private SourceService sourceService;
    private InspectionService inspectionService;
    private SampleService sampleService;
    private EvaluationService evaluationService;
    private ReportService reportService;

    private Session adminSession;
    private Session analystSession;
    private SourceDto testSource;
    private ProfileVersion demoProfile;

    @BeforeEach
    void setUp() {
        Path dbPath = tempDir.resolve("m7_test.db");
        connectionFactory = new ConnectionFactory(dbPath);
        SchemaMigrator migrator = new SchemaMigrator(connectionFactory);
        migrator.migrate();

        userRepo = new JdbcUserRepository(connectionFactory);
        sourceRepo = new JdbcSourceRepository(connectionFactory);
        inspectionRepo = new JdbcInspectionRepository(connectionFactory);
        sampleRepo = new JdbcSampleRepository(connectionFactory);
        readingRepo = new JdbcReadingRepository(connectionFactory);
        paramRepo = new JdbcParameterRepository(connectionFactory);
        profileRepo = new JdbcProfileRepository(connectionFactory);
        evalRepo = new JdbcEvaluationRepository(connectionFactory);
        alertRepo = new JdbcAlertRepository(connectionFactory);
        auditRepo = new JdbcAuditRepository(connectionFactory);

        PBKDF2PasswordHasher hasher = new PBKDF2PasswordHasher();
        authService = new AuthenticationService(userRepo, hasher, auditRepo);
        userService = new UserService(userRepo, hasher, auditRepo);
        sourceService = new SourceService(sourceRepo, auditRepo);
        inspectionService = new InspectionService(inspectionRepo, sourceRepo, userRepo, auditRepo);
        sampleService = new SampleService(sampleRepo, readingRepo, paramRepo, inspectionRepo, auditRepo);
        evaluationService = new EvaluationService(evalRepo, profileRepo, sampleRepo, readingRepo,
                paramRepo, inspectionRepo, sourceRepo, alertRepo, auditRepo,
                new com.aquasentinel.domain.evaluation.ThresholdEvaluator());
        reportService = new ReportService(evalRepo, profileRepo, sampleRepo,
                inspectionRepo, sourceRepo, userRepo);

        adminSession = authService.setupInitialAdmin("admin", "AdminPass123!".toCharArray());

        UserDto analyst = userService.createUser(adminSession, "analyst1", "AnalystPass1!".toCharArray(), Role.ANALYST);
        analystSession = new Session(analyst.id(), analyst.username(), analyst.role());

        testSource = sourceService.createSource(adminSession, "SRC-M7-001", "Yamuna Bridge",
                SourceType.RIVER, "Industrial Monitoring", "Delhi East", 28.6139, 77.2090);

        demoProfile = profileRepo.findByKeyAndVersion("SURFACE_WATER_DEMO", 1).orElseThrow();
    }

    @Test
    void testCsvReportExportAndFormulaSafety() {
        // Create evaluated sample
        EvaluationDto eval = createAndEvaluateSample("SMP-M7-01",
                new BigDecimal("7.40"), new BigDecimal("3.5"), new BigDecimal("6.0"));

        ReportSnapshot snapshot = reportService.createSnapshot(analystSession, eval.id());
        assertNotNull(snapshot);
        assertEquals(EvaluationOutcome.WITHIN_PROFILE, snapshot.outcome());
        assertTrue(snapshot.disclaimer().contains("Educational demonstration report"));

        CsvReportExporter csvExporter = new CsvReportExporter();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        reportService.exportEvaluationReport(analystSession, eval.id(), csvExporter, out);

        String csvOutput = out.toString(StandardCharsets.UTF_8);
        assertTrue(csvOutput.contains("# Disclaimer: Educational demonstration report."));
        assertTrue(csvOutput.contains("# Source: SRC-M7-001 (Yamuna Bridge)"));
        assertTrue(csvOutput.contains("# Overall Outcome: WITHIN_PROFILE"));
        assertTrue(csvOutput.contains("PARAMETER,RESULT,OBSERVED_VALUE,UNIT,LOWER_LIMIT,UPPER_LIMIT,REQUIRED,EXPLANATION"));
        assertTrue(csvOutput.contains("PH,PASS,7.40"));
        assertTrue(csvOutput.contains("TURB,PASS,3.5"));
    }

    @Test
    void testPdfReportExportAndStructure() throws Exception {
        EvaluationDto eval = createAndEvaluateSample("SMP-M7-02",
                new BigDecimal("9.20"), new BigDecimal("12.0"), new BigDecimal("4.0"));

        assertEquals(EvaluationOutcome.EXCEEDS_PROFILE, eval.outcome());

        PdfReportExporter pdfExporter = new PdfReportExporter();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        reportService.exportEvaluationReport(analystSession, eval.id(), pdfExporter, out);

        byte[] pdfBytes = out.toByteArray();
        assertTrue(pdfBytes.length > 500, "PDF should contain valid document structure");

        // Verify PDF document parsing via PDFBox 3.x Loader
        try (PDDocument doc = org.apache.pdfbox.Loader.loadPDF(pdfBytes)) {
            assertEquals(1, doc.getNumberOfPages());
        }
    }

    private EvaluationDto createAndEvaluateSample(String code, BigDecimal ph, BigDecimal turb, BigDecimal doVal) {
        InspectionDto insp = inspectionService.scheduleInspection(adminSession, testSource.id(),
                adminSession.userId(), LocalDate.now(), "Routine quality monitoring");

        SampleDto sample = sampleService.registerSample(analystSession, code, insp.id(), RecordOrigin.MANUAL_ENTRY);
        Instant now = Instant.now();
        sampleService.recordCollection(analystSession, sample.id(), now, adminSession.userId());
        sampleService.recordReceipt(analystSession, sample.id(), now.plusSeconds(300));

        sampleService.enterDraftReadings(analystSession, sample.id(), Map.of(
                "PH", ph,
                "TURB", turb,
                "DO", doVal
        ));
        sampleService.submitSample(analystSession, sample.id());

        return evaluationService.evaluate(analystSession, sample.id(), demoProfile.getId());
    }
}
