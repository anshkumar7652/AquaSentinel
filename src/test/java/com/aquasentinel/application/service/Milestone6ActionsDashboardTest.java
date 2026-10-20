package com.aquasentinel.application.service;

import com.aquasentinel.application.dto.ActionDto;
import com.aquasentinel.application.dto.AlertDto;
import com.aquasentinel.application.dto.DashboardDto;
import com.aquasentinel.application.dto.EvaluationDto;
import com.aquasentinel.application.dto.InspectionDto;
import com.aquasentinel.application.dto.SampleDto;
import com.aquasentinel.application.dto.SourceDto;
import com.aquasentinel.application.dto.UserDto;
import com.aquasentinel.application.security.Session;
import com.aquasentinel.domain.enums.ActionState;
import com.aquasentinel.domain.enums.AlertState;
import com.aquasentinel.domain.enums.EvaluationOutcome;
import com.aquasentinel.domain.enums.RecordOrigin;
import com.aquasentinel.domain.enums.Role;
import com.aquasentinel.domain.enums.SourceType;
import com.aquasentinel.domain.exception.ValidationException;
import com.aquasentinel.domain.model.ProfileVersion;
import com.aquasentinel.infrastructure.db.ConnectionFactory;
import com.aquasentinel.infrastructure.db.SchemaMigrator;
import com.aquasentinel.infrastructure.repository.JdbcActionRepository;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class Milestone6ActionsDashboardTest {

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
    private JdbcActionRepository actionRepo;
    private JdbcAuditRepository auditRepo;

    private AuthenticationService authService;
    private UserService userService;
    private SourceService sourceService;
    private InspectionService inspectionService;
    private SampleService sampleService;
    private EvaluationService evaluationService;
    private ActionService actionService;
    private DashboardService dashboardService;

    private Session adminSession;
    private Session analystSession;
    private Session officerSession;
    private UserDto officerUser;
    private SourceDto testSource;
    private ProfileVersion demoProfile;

    @BeforeEach
    void setUp() {
        Path dbPath = tempDir.resolve("m6_test.db");
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
        actionRepo = new JdbcActionRepository(connectionFactory);
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
        actionService = new ActionService(actionRepo, alertRepo, evalRepo, sampleRepo,
                inspectionRepo, sourceRepo, userRepo, auditRepo);
        dashboardService = new DashboardService(connectionFactory, sourceRepo, sampleRepo,
                alertRepo, paramRepo);

        adminSession = authService.setupInitialAdmin("admin", "AdminPass123!".toCharArray());

        UserDto analyst = userService.createUser(adminSession, "analyst1", "AnalystPass1!".toCharArray(), Role.ANALYST);
        analystSession = new Session(analyst.id(), analyst.username(), analyst.role());

        officerUser = userService.createUser(adminSession, "officer1", "OfficerPass1!".toCharArray(), Role.FIELD_OFFICER);
        officerSession = new Session(officerUser.id(), officerUser.username(), officerUser.role());

        testSource = sourceService.createSource(adminSession, "SRC-M6-001", "River M6 Station",
                SourceType.RIVER, "Surface Monitoring", "Haridwar", 29.9457, 78.1642);

        demoProfile = profileRepo.findByKeyAndVersion("SURFACE_WATER_DEMO", 1).orElseThrow();
    }

    @Test
    void testFullAlertActionClosureWorkflow() {
        // 1. Create a failing sample (e.g. turbidity = 12.5 exceeds limit 5.0)
        Instant t1 = Instant.parse("2026-10-15T10:00:00Z");
        EvaluationDto failedEval = createAndEvaluateSample("SPL-FAIL-01", t1,
                new BigDecimal("7.20"), new BigDecimal("12.5"), new BigDecimal("6.5"));

        assertEquals(EvaluationOutcome.EXCEEDS_PROFILE, failedEval.outcome());

        // 2. Alert should be automatically opened
        List<AlertDto> openAlerts = actionService.getAlerts(AlertState.OPEN);
        assertEquals(1, openAlerts.size());
        AlertDto alert = openAlerts.getFirst();
        assertEquals(failedEval.id(), alert.evaluationId());
        assertEquals(AlertState.OPEN, alert.state());

        // 3. Analyst acknowledges alert
        AlertDto ackedAlert = actionService.acknowledgeAlert(analystSession, alert.id());
        assertEquals(AlertState.ACKNOWLEDGED, ackedAlert.state());
        assertEquals(analystSession.userId(), ackedAlert.acknowledgedBy());

        // 4. Create corrective action assigned to field officer
        ActionDto action = actionService.createAction(
                analystSession, alert.id(), officerUser.id(),
                "Flush filter bed and re-calibrate intake monitor",
                LocalDate.of(2026, 10, 25)
        );
        assertEquals(ActionState.OPEN, action.state());
        assertEquals(officerUser.username(), action.assignedUsername());

        // 5. Officer marks IN_PROGRESS and AWAITING_RETEST
        ActionDto inProgress = actionService.updateState(officerSession, action.id(), ActionState.IN_PROGRESS);
        assertEquals(ActionState.IN_PROGRESS, inProgress.state());

        ActionDto awaitingRetest = actionService.updateState(officerSession, action.id(), ActionState.AWAITING_RETEST);
        assertEquals(ActionState.AWAITING_RETEST, awaitingRetest.state());

        // 6. Attempt closure without valid follow-up evaluation must fail
        assertThrows(ValidationException.class, () ->
                actionService.closeAction(officerSession, action.id(), "Flushed filter", null));

        // 7. Attempt closure with earlier timestamp or wrong source must fail
        Instant earlierTime = Instant.parse("2026-10-14T10:00:00Z");
        EvaluationDto earlierPassingEval = createAndEvaluateSample("SPL-EARLY-PASS", earlierTime,
                new BigDecimal("7.20"), new BigDecimal("2.0"), new BigDecimal("6.5"));
        assertEquals(EvaluationOutcome.WITHIN_PROFILE, earlierPassingEval.outcome());

        assertThrows(ValidationException.class, () ->
                actionService.closeAction(officerSession, action.id(), "Flushed filter", earlierPassingEval.id()));

        // 8. Create valid passing follow-up retest (later collection timestamp, within profile)
        Instant t2 = Instant.parse("2026-10-16T14:00:00Z");
        EvaluationDto passingRetest = createAndEvaluateSample("SPL-PASS-02", t2,
                new BigDecimal("7.30"), new BigDecimal("1.5"), new BigDecimal("6.2"));
        assertEquals(EvaluationOutcome.WITHIN_PROFILE, passingRetest.outcome());

        // 9. Officer closes action with completion notes and passing follow-up
        ActionDto closedAction = actionService.closeAction(
                officerSession, action.id(), "Filter bed flushed thoroughly, retest shows 1.5 NTU", passingRetest.id());

        assertEquals(ActionState.CLOSED, closedAction.state());
        assertEquals(passingRetest.id(), closedAction.followupEvaluationId());
        assertNotNull(closedAction.closedAt());

        // 10. Alert should now be automatically resolved
        List<AlertDto> resolvedAlerts = actionService.getAlerts(AlertState.RESOLVED);
        assertEquals(1, resolvedAlerts.size());
        assertEquals(alert.id(), resolvedAlerts.getFirst().id());

        // 11. Dashboard KPIs reflect accurate counts
        DashboardDto.KpiSummary kpi = dashboardService.getKpiSummary();
        assertEquals(1, kpi.activeSourcesCount());
        assertEquals(3, kpi.samplesInPeriodCount()); // 1 fail + 1 early pass + 1 retest pass
        assertEquals(1, kpi.profileViolationsCount());
        assertEquals(0, kpi.unresolvedAlertsCount()); // alert was resolved!
    }

    @Test
    void testDashboardPointSampleTrendQuery() {
        UUID paramTurbidityId = paramRepo.findByCode("TURB").orElseThrow().getId();

        Instant t1 = Instant.parse("2026-10-10T08:00:00Z");
        Instant t2 = Instant.parse("2026-10-12T08:00:00Z");
        Instant t3 = Instant.parse("2026-10-14T08:00:00Z");

        createAndEvaluateSample("TR-SPL-01", t1,
                new BigDecimal("7.00"), new BigDecimal("3.2"), new BigDecimal("6.5"));
        createAndEvaluateSample("TR-SPL-02", t2,
                new BigDecimal("7.10"), new BigDecimal("4.8"), new BigDecimal("6.2"));
        createAndEvaluateSample("TR-SPL-03", t3,
                new BigDecimal("7.20"), new BigDecimal("2.1"), new BigDecimal("6.8"));

        DashboardDto.TrendSeries trend = dashboardService.getPointSampleTrend(
                testSource.id(), paramTurbidityId,
                Instant.parse("2026-10-01T00:00:00Z"),
                Instant.parse("2026-10-20T00:00:00Z")
        );

        assertEquals("TURB", trend.parameterCode());
        assertEquals("NTU", trend.unit());
        assertEquals(testSource.name(), trend.sourceName());
        assertEquals(3, trend.points().size());
        assertEquals(new BigDecimal("3.2"), trend.points().get(0).value());
        assertEquals(new BigDecimal("4.8"), trend.points().get(1).value());
        assertEquals(new BigDecimal("2.1"), trend.points().get(2).value());
    }

    private EvaluationDto createAndEvaluateSample(String code, Instant collectedAt,
                                                 BigDecimal phVal, BigDecimal turbVal, BigDecimal doVal) {
        InspectionDto insp = inspectionService.scheduleInspection(adminSession, testSource.id(),
                adminSession.userId(), LocalDate.now(), "Routine inspection");

        SampleDto sample = sampleService.registerSample(analystSession, code, insp.id(), RecordOrigin.MANUAL_ENTRY);
        sampleService.recordCollection(analystSession, sample.id(), collectedAt, adminSession.userId());
        sampleService.recordReceipt(analystSession, sample.id(), collectedAt.plusSeconds(300));

        sampleService.enterDraftReadings(analystSession, sample.id(), Map.of(
                "PH", phVal,
                "TURB", turbVal,
                "DO", doVal
        ));
        sampleService.submitSample(analystSession, sample.id());

        return evaluationService.evaluate(analystSession, sample.id(), demoProfile.getId());
    }
}
