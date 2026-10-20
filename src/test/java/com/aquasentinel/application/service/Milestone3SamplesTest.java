package com.aquasentinel.application.service;

import com.aquasentinel.application.dto.InspectionDto;
import com.aquasentinel.application.dto.ReadingDto;
import com.aquasentinel.application.dto.SampleDto;
import com.aquasentinel.application.dto.SourceDto;
import com.aquasentinel.application.dto.UserDto;
import com.aquasentinel.application.security.Session;
import com.aquasentinel.domain.enums.RecordOrigin;
import com.aquasentinel.domain.enums.Role;
import com.aquasentinel.domain.enums.SampleStatus;
import com.aquasentinel.domain.enums.SourceType;
import com.aquasentinel.domain.exception.AuthorizationException;
import com.aquasentinel.domain.exception.ValidationException;
import com.aquasentinel.infrastructure.db.ConnectionFactory;
import com.aquasentinel.infrastructure.db.SchemaMigrator;
import com.aquasentinel.infrastructure.repository.JdbcAuditRepository;
import com.aquasentinel.infrastructure.repository.JdbcInspectionRepository;
import com.aquasentinel.infrastructure.repository.JdbcParameterRepository;
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
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class Milestone3SamplesTest {

    @TempDir
    Path tempDir;

    private AuthenticationService authService;
    private UserService userService;
    private SourceService sourceService;
    private InspectionService inspectionService;
    private SampleService sampleService;

    private Session adminSession;
    private Session analystSession;
    private Session viewerSession;
    private SourceDto source;
    private InspectionDto inspection;

    @BeforeEach
    void setUp() {
        Path dbPath = tempDir.resolve("m3-test.db");
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
        JdbcAuditRepository auditRepo = new JdbcAuditRepository(connectionFactory);

        authService = new AuthenticationService(userRepo, hasher, auditRepo);
        userService = new UserService(userRepo, hasher, auditRepo);
        sourceService = new SourceService(sourceRepo, auditRepo);
        inspectionService = new InspectionService(inspectionRepo, sourceRepo, userRepo, auditRepo);
        sampleService = new SampleService(sampleRepo, readingRepo, paramRepo, inspectionRepo, auditRepo);

        adminSession = authService.setupInitialAdmin("admin", "AdminPass123!".toCharArray());
        UserDto analyst = userService.createUser(adminSession, "analyst1", "AnalystPass1!".toCharArray(), Role.ANALYST);
        analystSession = new Session(analyst.id(), analyst.username(), analyst.role());

        UserDto viewer = userService.createUser(adminSession, "viewer1", "ViewerPass1!".toCharArray(), Role.VIEWER);
        viewerSession = new Session(viewer.id(), viewer.username(), viewer.role());

        source = sourceService.createSource(adminSession, "SRC-M3", "Yamuna Bridge", SourceType.RIVER,
                "Irrigation", "Section 4", 28.6, 77.2);
        inspection = inspectionService.scheduleInspection(adminSession, source.id(), adminSession.userId(),
                LocalDate.now().plusDays(2), "Sampling inspection");
    }

    @Test
    void testSampleRegistrationAndUniqueCodeConstraint() {
        // Viewer cannot register
        assertThrows(AuthorizationException.class, () ->
                sampleService.registerSample(viewerSession, "SMP-001", inspection.id(), RecordOrigin.MANUAL_ENTRY));

        // Analyst registers sample
        SampleDto sample = sampleService.registerSample(analystSession, "SMP-001", inspection.id(), RecordOrigin.MANUAL_ENTRY);
        assertNotNull(sample);
        assertEquals("SMP-001", sample.code());
        assertEquals(SampleStatus.REGISTERED, sample.status());

        // Duplicate code fails
        assertThrows(ValidationException.class, () ->
                sampleService.registerSample(analystSession, "SMP-001", inspection.id(), RecordOrigin.MANUAL_ENTRY));
    }

    @Test
    void testSampleLifecycleAndReceiptValidation() {
        SampleDto sample = sampleService.registerSample(analystSession, "SMP-002", inspection.id(), RecordOrigin.MANUAL_ENTRY);

        Instant collectTime = Instant.now();
        sampleService.recordCollection(analystSession, sample.id(), collectTime, adminSession.userId());

        SampleDto collected = sampleService.getSample(analystSession, sample.id()).orElseThrow();
        assertEquals(SampleStatus.COLLECTED, collected.status());

        // Receipt time cannot precede collection time
        Instant invalidReceiptTime = collectTime.minusSeconds(3600);
        assertThrows(ValidationException.class, () ->
                sampleService.recordReceipt(analystSession, sample.id(), invalidReceiptTime));

        // Valid receipt time succeeds
        Instant validReceiptTime = collectTime.plusSeconds(1800);
        sampleService.recordReceipt(analystSession, sample.id(), validReceiptTime);

        SampleDto received = sampleService.getSample(analystSession, sample.id()).orElseThrow();
        assertEquals(SampleStatus.RECEIVED, received.status());
    }

    @Test
    void testReadingEntryValidationAndSubmissionLock() {
        SampleDto sample = sampleService.registerSample(analystSession, "SMP-003", inspection.id(), RecordOrigin.MANUAL_ENTRY);
        Instant collectTime = Instant.now();
        sampleService.recordCollection(analystSession, sample.id(), collectTime, adminSession.userId());
        sampleService.recordReceipt(analystSession, sample.id(), collectTime.plusSeconds(600));

        // pH bounds check: 0.0 to 14.0
        assertThrows(ValidationException.class, () ->
                sampleService.enterDraftReadings(analystSession, sample.id(), Map.of("PH", new BigDecimal("15.5"))));

        // Valid readings entered
        sampleService.enterDraftReadings(analystSession, sample.id(), Map.of(
                "PH", new BigDecimal("7.35"),
                "TURB", new BigDecimal("4.2"),
                "DO", new BigDecimal("6.8")
        ));

        SampleDto draft = sampleService.getSample(analystSession, sample.id()).orElseThrow();
        assertEquals(SampleStatus.ANALYSIS_DRAFT, draft.status());

        List<ReadingDto> readings = sampleService.getReadings(analystSession, sample.id());
        assertEquals(3, readings.size());

        // Submit analysis
        sampleService.submitSample(analystSession, sample.id());
        SampleDto submitted = sampleService.getSample(analystSession, sample.id()).orElseThrow();
        assertEquals(SampleStatus.SUBMITTED, submitted.status());

        // Modifying submitted readings is blocked
        assertThrows(ValidationException.class, () ->
                sampleService.enterDraftReadings(analystSession, sample.id(), Map.of("PH", new BigDecimal("7.40"))));
    }

    @Test
    void testSampleRevisionTracking() {
        SampleDto sample = sampleService.registerSample(analystSession, "SMP-004", inspection.id(), RecordOrigin.MANUAL_ENTRY);
        Instant now = Instant.now();
        sampleService.recordCollection(analystSession, sample.id(), now, adminSession.userId());
        sampleService.recordReceipt(analystSession, sample.id(), now.plusSeconds(300));
        sampleService.enterDraftReadings(analystSession, sample.id(), Map.of("PH", new BigDecimal("7.20")));
        sampleService.submitSample(analystSession, sample.id());

        // Create revision for correction
        SampleDto revision = sampleService.createRevision(analystSession, sample.id(), "SMP-004-REV1");
        assertNotNull(revision);
        assertEquals("SMP-004-REV1", revision.code());
        assertEquals(SampleStatus.ANALYSIS_DRAFT, revision.status());
        assertEquals(sample.id(), revision.supersedesSampleId());

        // Revision copies readings and allows draft edits
        List<ReadingDto> revReadings = sampleService.getReadings(analystSession, revision.id());
        assertEquals(1, revReadings.size());
        assertEquals(new BigDecimal("7.20"), revReadings.get(0).valueDecimal());

        // Can edit revision readings
        assertDoesNotThrow(() ->
                sampleService.enterDraftReadings(analystSession, revision.id(), Map.of("PH", new BigDecimal("7.25"))));
    }
}
