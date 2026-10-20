package com.aquasentinel.app;

import com.aquasentinel.application.port.ActionRepository;
import com.aquasentinel.application.port.AlertRepository;
import com.aquasentinel.application.port.AuditRepository;
import com.aquasentinel.application.port.EvaluationRepository;
import com.aquasentinel.application.port.ImportBatchRepository;
import com.aquasentinel.application.port.InspectionRepository;
import com.aquasentinel.application.port.ParameterRepository;
import com.aquasentinel.application.port.ProfileRepository;
import com.aquasentinel.application.port.ReadingRepository;
import com.aquasentinel.application.port.SampleRepository;
import com.aquasentinel.application.port.SourceRepository;
import com.aquasentinel.application.port.UserRepository;
import com.aquasentinel.application.service.ActionService;
import com.aquasentinel.application.service.AuthenticationService;
import com.aquasentinel.application.service.DashboardService;
import com.aquasentinel.application.service.EvaluationService;
import com.aquasentinel.application.service.ImportService;
import com.aquasentinel.application.service.InspectionService;
import com.aquasentinel.application.service.ReportService;
import com.aquasentinel.application.service.SampleService;
import com.aquasentinel.application.service.SourceService;
import com.aquasentinel.application.service.UserService;
import com.aquasentinel.domain.evaluation.ThresholdEvaluator;
import com.aquasentinel.infrastructure.db.ConnectionFactory;
import com.aquasentinel.infrastructure.db.SchemaMigrator;
import com.aquasentinel.infrastructure.repository.JdbcAlertRepository;
import com.aquasentinel.infrastructure.repository.JdbcAuditRepository;
import com.aquasentinel.infrastructure.repository.JdbcEvaluationRepository;
import com.aquasentinel.infrastructure.repository.JdbcImportBatchRepository;
import com.aquasentinel.infrastructure.repository.JdbcInspectionRepository;
import com.aquasentinel.infrastructure.repository.JdbcParameterRepository;
import com.aquasentinel.infrastructure.repository.JdbcProfileRepository;
import com.aquasentinel.infrastructure.repository.JdbcReadingRepository;
import com.aquasentinel.infrastructure.repository.JdbcSampleRepository;
import com.aquasentinel.infrastructure.repository.JdbcSourceRepository;
import com.aquasentinel.infrastructure.repository.JdbcUserRepository;
import com.aquasentinel.infrastructure.security.PBKDF2PasswordHasher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Composition root wiring application services and database connections via constructor injection.
 */
public class AppBootstrap {
    private static final Logger log = LoggerFactory.getLogger(AppBootstrap.class);

    private final AppContext context;
    private final ConnectionFactory connectionFactory;
    private final SchemaMigrator schemaMigrator;

    private final UserRepository userRepository;
    private final SourceRepository sourceRepository;
    private final InspectionRepository inspectionRepository;
    private final ParameterRepository parameterRepository;
    private final SampleRepository sampleRepository;
    private final ReadingRepository readingRepository;
    private final ProfileRepository profileRepository;
    private final EvaluationRepository evaluationRepository;
    private final AlertRepository alertRepository;
    private final ActionRepository actionRepository;
    private final ImportBatchRepository importBatchRepository;
    private final AuditRepository auditRepository;
    private final PBKDF2PasswordHasher passwordHasher;
    private final ThresholdEvaluator thresholdEvaluator;

    private final AuthenticationService authenticationService;
    private final UserService userService;
    private final SourceService sourceService;
    private final InspectionService inspectionService;
    private final SampleService sampleService;
    private final EvaluationService evaluationService;
    private final ImportService importService;
    private final ActionService actionService;
    private final DashboardService dashboardService;
    private final ReportService reportService;

    public AppBootstrap(AppContext context) {
        this.context = context;
        this.connectionFactory = new ConnectionFactory(context.databasePath());
        this.schemaMigrator = new SchemaMigrator(connectionFactory);

        this.userRepository = new JdbcUserRepository(connectionFactory);
        this.sourceRepository = new JdbcSourceRepository(connectionFactory);
        this.inspectionRepository = new JdbcInspectionRepository(connectionFactory);
        this.parameterRepository = new JdbcParameterRepository(connectionFactory);
        this.sampleRepository = new JdbcSampleRepository(connectionFactory);
        this.readingRepository = new JdbcReadingRepository(connectionFactory);
        this.profileRepository = new JdbcProfileRepository(connectionFactory);
        this.evaluationRepository = new JdbcEvaluationRepository(connectionFactory);
        this.alertRepository = new JdbcAlertRepository(connectionFactory);
        this.actionRepository = new com.aquasentinel.infrastructure.repository.JdbcActionRepository(connectionFactory);
        this.importBatchRepository = new JdbcImportBatchRepository(connectionFactory);
        this.auditRepository = new JdbcAuditRepository(connectionFactory);
        this.passwordHasher = new PBKDF2PasswordHasher();
        this.thresholdEvaluator = new ThresholdEvaluator();

        this.authenticationService = new AuthenticationService(userRepository, passwordHasher, auditRepository);
        this.userService = new UserService(userRepository, passwordHasher, auditRepository);
        this.sourceService = new SourceService(sourceRepository, auditRepository);
        this.inspectionService = new InspectionService(inspectionRepository, sourceRepository, userRepository, auditRepository);
        this.sampleService = new SampleService(sampleRepository, readingRepository, parameterRepository, inspectionRepository, auditRepository);
        this.evaluationService = new EvaluationService(evaluationRepository, profileRepository, sampleRepository, readingRepository,
                parameterRepository, inspectionRepository, sourceRepository, alertRepository, auditRepository, thresholdEvaluator);
        this.importService = new ImportService(importBatchRepository, sourceRepository, parameterRepository,
                inspectionRepository, sampleRepository, readingRepository, auditRepository);
        this.actionService = new ActionService(actionRepository, alertRepository, evaluationRepository,
                sampleRepository, inspectionRepository, sourceRepository, userRepository, auditRepository);
        this.dashboardService = new DashboardService(connectionFactory, sourceRepository, sampleRepository,
                alertRepository, parameterRepository);
        this.reportService = new ReportService(evaluationRepository, profileRepository, sampleRepository,
                inspectionRepository, sourceRepository, userRepository);
    }

    public void initialize() {
        log.info("Bootstrapping AquaSentinel at data directory: {}", context.dataDirectory());
        schemaMigrator.migrate();

        com.aquasentinel.application.security.Session adminSession = getOrCreateAdminSession();

        // If the database has no water sources seeded yet, populate initial demonstration data
        if (sourceRepository.findAll(0, 1).isEmpty()) {
            log.info("Initial empty database detected: auto-seeding baseline educational demonstration data...");
            seedDemoData(adminSession);
        }

        log.info("Database initialized successfully at: {}", context.databasePath());
    }

    public com.aquasentinel.application.security.Session getOrCreateAdminSession() {
        if (authenticationService.isSetupRequired()) {
            log.info("Creating initial administrator account: admin");
            return authenticationService.setupInitialAdmin("admin", "Admin123!".toCharArray());
        }
        return userRepository.findByUsername("admin")
                .map(u -> new com.aquasentinel.application.security.Session(u.getId(), u.getUsername(), u.getRole()))
                .orElseGet(() -> {
                    // Fallback administrator session if user records are present but username differs
                    var all = userRepository.findAll();
                    if (!all.isEmpty()) {
                        var first = all.getFirst();
                        return new com.aquasentinel.application.security.Session(first.getId(), first.getUsername(), first.getRole());
                    }
                    return new com.aquasentinel.application.security.Session(java.util.UUID.randomUUID(), "admin", com.aquasentinel.domain.enums.Role.ADMINISTRATOR);
                });
    }

    public void seedDemoData(com.aquasentinel.application.security.Session adminSession) {
        try {
            log.info("Seeding realistic educational demo sources, samples, and evaluations...");

            // 1. Ensure analyst and field officer users exist
            com.aquasentinel.application.dto.UserDto analyst;
            if (userRepository.findByUsername("analyst1").isEmpty()) {
                analyst = userService.createUser(adminSession, "analyst1", "Analyst123!".toCharArray(), com.aquasentinel.domain.enums.Role.ANALYST);
            } else {
                var u = userRepository.findByUsername("analyst1").get();
                analyst = new com.aquasentinel.application.dto.UserDto(u.getId(), u.getUsername(), u.getRole(), u.isActive(), u.getCreatedAt());
            }

            com.aquasentinel.application.security.Session analystSession =
                    new com.aquasentinel.application.security.Session(analyst.id(), analyst.username(), analyst.role());

            // 2. Create 3 monitored water sources
            var s1 = sourceService.createSource(adminSession, "SRC-DEL-01", "Yamuna River - Wazirabad",
                    com.aquasentinel.domain.enums.SourceType.RIVER, "Municipal Supply & Irrigation", "Delhi North", 28.7139, 77.2280);

            var s2 = sourceService.createSource(adminSession, "SRC-DEL-02", "Okhla Barrage Monitoring Station",
                    com.aquasentinel.domain.enums.SourceType.RIVER, "Ecological & Industrial", "Delhi South", 28.5448, 77.3090);

            var s3 = sourceService.createSource(adminSession, "SRC-DEL-03", "Hauz Khas Lake Point",
                    com.aquasentinel.domain.enums.SourceType.LAKE, "Recreation & Biodiversity", "Delhi South", 28.5494, 77.1942);

            // 3. Schedule inspection
            var insp = inspectionService.scheduleInspection(adminSession, s1.id(), adminSession.userId(),
                    java.time.LocalDate.now().minusDays(2), "Quarterly surface water monitoring tour");

            // 4. Find demo profile (SURFACE_WATER_DEMO v1)
            var profileOpt = profileRepository.findByKeyAndVersion("SURFACE_WATER_DEMO", 1);
            if (profileOpt.isEmpty()) {
                log.warn("SURFACE_WATER_DEMO v1 not found; skipping sample evaluations.");
                return;
            }
            java.util.UUID profileId = profileOpt.get().getId();

            java.time.Instant now = java.time.Instant.now();

            // Sample 1: Baseline sample (Passing within profile)
            var smp1 = sampleService.registerSample(analystSession, "SMP-DEL-01", insp.id(), com.aquasentinel.domain.enums.RecordOrigin.MANUAL_ENTRY);
            sampleService.recordCollection(analystSession, smp1.id(), now.minusSeconds(172800), adminSession.userId());
            sampleService.recordReceipt(analystSession, smp1.id(), now.minusSeconds(172000));
            sampleService.enterDraftReadings(analystSession, smp1.id(), java.util.Map.of(
                    "PH", new java.math.BigDecimal("7.45"),
                    "TURB", new java.math.BigDecimal("3.8"),
                    "DO", new java.math.BigDecimal("6.2"),
                    "TDS", new java.math.BigDecimal("380.0")
            ));
            sampleService.submitSample(analystSession, smp1.id());
            evaluationService.evaluate(analystSession, smp1.id(), profileId);

            // Sample 2: Non-compliant sample (Turbidity 14.5 exceeds 5.0, pH 9.1 exceeds 8.5 -> EXCEEDS_PROFILE -> Opens Alert)
            var smp2 = sampleService.registerSample(analystSession, "SMP-DEL-02", insp.id(), com.aquasentinel.domain.enums.RecordOrigin.MANUAL_ENTRY);
            sampleService.recordCollection(analystSession, smp2.id(), now.minusSeconds(86400), adminSession.userId());
            sampleService.recordReceipt(analystSession, smp2.id(), now.minusSeconds(85000));
            sampleService.enterDraftReadings(analystSession, smp2.id(), java.util.Map.of(
                    "PH", new java.math.BigDecimal("9.10"),
                    "TURB", new java.math.BigDecimal("14.5"),
                    "DO", new java.math.BigDecimal("3.8"),
                    "TDS", new java.math.BigDecimal("520.0")
            ));
            sampleService.submitSample(analystSession, smp2.id());
            var eval2 = evaluationService.evaluate(analystSession, smp2.id(), profileId);

            // Sample 3: Recent compliant sample (Passing within profile)
            var smp3 = sampleService.registerSample(analystSession, "SMP-DEL-03", insp.id(), com.aquasentinel.domain.enums.RecordOrigin.MANUAL_ENTRY);
            sampleService.recordCollection(analystSession, smp3.id(), now.minusSeconds(14400), adminSession.userId());
            sampleService.recordReceipt(analystSession, smp3.id(), now.minusSeconds(13000));
            sampleService.enterDraftReadings(analystSession, smp3.id(), java.util.Map.of(
                    "PH", new java.math.BigDecimal("7.20"),
                    "TURB", new java.math.BigDecimal("2.1"),
                    "DO", new java.math.BigDecimal("6.8"),
                    "TDS", new java.math.BigDecimal("340.0")
            ));
            sampleService.submitSample(analystSession, smp3.id());
            evaluationService.evaluate(analystSession, smp3.id(), profileId);

            // 5. Open Alert and Corrective Action for Sample 2
            var openAlerts = alertRepository.findByState(com.aquasentinel.domain.enums.AlertState.OPEN);
            if (!openAlerts.isEmpty()) {
                var alert = openAlerts.getFirst();
                actionService.acknowledgeAlert(analystSession, alert.id());
                actionService.createAction(
                        analystSession,
                        alert.id(),
                        adminSession.userId(),
                        "Deploy aeration boom, inspect industrial discharge point, and schedule verified retest.",
                        java.time.LocalDate.now().plusDays(3)
                );
            }

            log.info("Educational demo data successfully seeded: 3 sources, 3 evaluated samples, 1 active alert with corrective action.");
        } catch (Exception e) {
            log.warn("Could not seed demo data: {}", e.getMessage(), e);
        }
    }

    public AppContext getContext() { return context; }
    public ConnectionFactory getConnectionFactory() { return connectionFactory; }
    public AuthenticationService getAuthenticationService() { return authenticationService; }
    public UserService getUserService() { return userService; }
    public SourceService getSourceService() { return sourceService; }
    public InspectionService getInspectionService() { return inspectionService; }
    public SampleService getSampleService() { return sampleService; }
    public EvaluationService getEvaluationService() { return evaluationService; }
    public ParameterRepository getParameterRepository() { return parameterRepository; }
    public SampleRepository getSampleRepository() { return sampleRepository; }
    public ReadingRepository getReadingRepository() { return readingRepository; }
    public ProfileRepository getProfileRepository() { return profileRepository; }
    public EvaluationRepository getEvaluationRepository() { return evaluationRepository; }
    public AlertRepository getAlertRepository() { return alertRepository; }
    public ActionRepository getActionRepository() { return actionRepository; }
    public ImportBatchRepository getImportBatchRepository() { return importBatchRepository; }
    public ImportService getImportService() { return importService; }
    public ActionService getActionService() { return actionService; }
    public DashboardService getDashboardService() { return dashboardService; }
    public ReportService getReportService() { return reportService; }
    public UserRepository getUserRepository() { return userRepository; }
    public SourceRepository getSourceRepository() { return sourceRepository; }
    public InspectionRepository getInspectionRepository() { return inspectionRepository; }
    public AuditRepository getAuditRepository() { return auditRepository; }
}
