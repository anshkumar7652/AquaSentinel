package com.aquasentinel.application.service;

import com.aquasentinel.application.dto.EvaluationDto;
import com.aquasentinel.application.dto.InspectionDto;
import com.aquasentinel.application.dto.SampleDto;
import com.aquasentinel.application.dto.SourceDto;
import com.aquasentinel.application.dto.UserDto;
import com.aquasentinel.application.port.AlertRepository;
import com.aquasentinel.application.port.ProfileRepository;
import com.aquasentinel.application.security.Session;
import com.aquasentinel.domain.enums.AlertState;
import com.aquasentinel.domain.enums.EvaluationOutcome;
import com.aquasentinel.domain.enums.RecordOrigin;
import com.aquasentinel.domain.enums.Role;
import com.aquasentinel.domain.enums.SampleStatus;
import com.aquasentinel.domain.enums.SourceType;
import com.aquasentinel.domain.evaluation.ThresholdEvaluator;
import com.aquasentinel.domain.model.ProfileVersion;
import com.aquasentinel.infrastructure.db.ConnectionFactory;
import com.aquasentinel.infrastructure.db.SchemaMigrator;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class Milestone4EvaluationIntegrationTest {

    @TempDir
    Path tempDir;

    private AuthenticationService authService;
    private UserService userService;
    private SourceService sourceService;
    private InspectionService inspectionService;
    private SampleService sampleService;
    private EvaluationService evaluationService;
    private AlertRepository alertRepo;
    private ProfileRepository profileRepo;

    private Session adminSession;
    private Session analystSession;
    private SourceDto riverSource;
    private InspectionDto inspection;
    private ProfileVersion demoProfile;

    @BeforeEach
    void setUp() {
        Path dbPath = tempDir.resolve("m4-test.db");
        ConnectionFactory connectionFactory = new ConnectionFactory(dbPath);
        SchemaMigrator migrator = new SchemaMigrator(connectionFactory);
        migrator.migrate();

        PBKDF2PasswordHasher hasher = new PBKDF2PasswordHasher();
        JdbcUserRepository userRepo = new JdbcUserRepository(connectionFactory);
        JdbcSourceRepository sourceRepo = new JdbcSourceRepository(connectionFactory);
        JdbcInspectionRepository inspectionRepo = new JdbcInspectionRepository(connectionFactory);
        JdbcParameterRepository paramRepo = new JdbcParameterRepository(connectionFactory);
        JdbcSampleRepository sampleRepo = new JdbcSampleRepository(connectionFactory);
        JdbcReadingRepository readingRepo = new JdbcReadingRepository(connectionFactory);
        profileRepo = new JdbcProfileRepository(connectionFactory);
        JdbcEvaluationRepository evalRepo = new JdbcEvaluationRepository(connectionFactory);
        alertRepo = new JdbcAlertRepository(connectionFactory);
        JdbcAuditRepository auditRepo = new JdbcAuditRepository(connectionFactory);
        ThresholdEvaluator evaluator = new ThresholdEvaluator();

        authService = new AuthenticationService(userRepo, hasher, auditRepo);
        userService = new UserService(userRepo, hasher, auditRepo);
        sourceService = new SourceService(sourceRepo, auditRepo);
        inspectionService = new InspectionService(inspectionRepo, sourceRepo, userRepo, auditRepo);
        sampleService = new SampleService(sampleRepo, readingRepo, paramRepo, inspectionRepo, auditRepo);
        evaluationService = new EvaluationService(evalRepo, profileRepo, sampleRepo, readingRepo,
                paramRepo, inspectionRepo, sourceRepo, alertRepo, auditRepo, evaluator);

        adminSession = authService.setupInitialAdmin("admin", "AdminPass123!".toCharArray());
        UserDto analyst = userService.createUser(adminSession, "analyst1", "AnalystPass1!".toCharArray(), Role.ANALYST);
        analystSession = new Session(analyst.id(), analyst.username(), analyst.role());

        riverSource = sourceService.createSource(adminSession, "SRC-RIV-1", "Ganga Sampling Point",
                SourceType.RIVER, "Surface Monitoring", "Haridwar ghat", 29.9457, 78.1642);
        inspection = inspectionService.scheduleInspection(adminSession, riverSource.id(),
                adminSession.userId(), LocalDate.now().plusDays(1), "Routine run");

        demoProfile = profileRepo.findByKeyAndVersion("SURFACE_WATER_DEMO", 1).orElseThrow();
    }

    @Test
    void testPassingSampleEvaluationPersistsWithinProfile() {
        SampleDto sample = sampleService.registerSample(analystSession, "SMP-PASS-1", inspection.id(), RecordOrigin.MANUAL_ENTRY);
        Instant now = Instant.now();
        sampleService.recordCollection(analystSession, sample.id(), now, adminSession.userId());
        sampleService.recordReceipt(analystSession, sample.id(), now.plusSeconds(300));

        // pH 7.2 (limit 6.5-8.5), Turbidity 3.0 (limit <= 5.0), DO 6.5 (limit >= 5.0)
        sampleService.enterDraftReadings(analystSession, sample.id(), Map.of(
                "PH", new BigDecimal("7.20"),
                "TURB", new BigDecimal("3.0"),
                "DO", new BigDecimal("6.5")
        ));
        sampleService.submitSample(analystSession, sample.id());

        EvaluationDto eval = evaluationService.evaluate(analystSession, sample.id(), demoProfile.getId());
        assertNotNull(eval);
        assertEquals(EvaluationOutcome.WITHIN_PROFILE, eval.outcome());
        assertNotNull(eval.inputDigest());

        // Sample status updated to EVALUATED
        SampleDto evaluatedSample = sampleService.getSample(analystSession, sample.id()).orElseThrow();
        assertEquals(SampleStatus.EVALUATED, evaluatedSample.status());

        // No alert should be generated for passing evaluation
        assertEquals(0, alertRepo.countUnresolved());
    }

    @Test
    void testViolatedSampleEvaluationCreatesAlertAtomically() {
        SampleDto sample = sampleService.registerSample(analystSession, "SMP-FAIL-1", inspection.id(), RecordOrigin.MANUAL_ENTRY);
        Instant now = Instant.now();
        sampleService.recordCollection(analystSession, sample.id(), now, adminSession.userId());
        sampleService.recordReceipt(analystSession, sample.id(), now.plusSeconds(300));

        // pH 9.1 exceeds 8.5
        sampleService.enterDraftReadings(analystSession, sample.id(), Map.of(
                "PH", new BigDecimal("9.10"),
                "TURB", new BigDecimal("2.5"),
                "DO", new BigDecimal("6.0")
        ));
        sampleService.submitSample(analystSession, sample.id());

        EvaluationDto eval = evaluationService.evaluate(analystSession, sample.id(), demoProfile.getId());
        assertEquals(EvaluationOutcome.EXCEEDS_PROFILE, eval.outcome());

        // Alert must be created in OPEN state
        assertEquals(1, alertRepo.countUnresolved());
        var alert = alertRepo.findByEvaluationId(eval.id()).orElseThrow();
        assertEquals(AlertState.OPEN, alert.state());
    }

    @Test
    void testEvaluationIsIdempotent() {
        SampleDto sample = sampleService.registerSample(analystSession, "SMP-IDEM-1", inspection.id(), RecordOrigin.MANUAL_ENTRY);
        Instant now = Instant.now();
        sampleService.recordCollection(analystSession, sample.id(), now, adminSession.userId());
        sampleService.recordReceipt(analystSession, sample.id(), now.plusSeconds(300));
        sampleService.enterDraftReadings(analystSession, sample.id(), Map.of(
                "PH", new BigDecimal("7.0"), "TURB", new BigDecimal("1.0"), "DO", new BigDecimal("7.0")
        ));
        sampleService.submitSample(analystSession, sample.id());

        EvaluationDto first = evaluationService.evaluate(analystSession, sample.id(), demoProfile.getId());
        EvaluationDto second = evaluationService.evaluate(analystSession, sample.id(), demoProfile.getId());

        assertEquals(first.id(), second.id(), "Re-evaluating the same sample and profile must return existing evaluation record");
        assertEquals(first.inputDigest(), second.inputDigest());
    }
}
