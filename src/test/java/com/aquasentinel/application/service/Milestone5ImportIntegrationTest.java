package com.aquasentinel.application.service;

import com.aquasentinel.application.dto.ImportPreviewDto;
import com.aquasentinel.application.dto.ImportPreviewDto.ImportMetadata;
import com.aquasentinel.application.dto.ImportPreviewDto.ImportReceipt;
import com.aquasentinel.application.dto.SourceDto;
import com.aquasentinel.application.dto.UserDto;
import com.aquasentinel.application.port.ImportBatchRepository;
import com.aquasentinel.application.port.ReadingRepository;
import com.aquasentinel.application.port.SampleRepository;
import com.aquasentinel.application.security.Session;
import com.aquasentinel.domain.enums.RecordOrigin;
import com.aquasentinel.domain.enums.Role;
import com.aquasentinel.domain.enums.SourceType;
import com.aquasentinel.domain.exception.ValidationException;
import com.aquasentinel.infrastructure.db.ConnectionFactory;
import com.aquasentinel.infrastructure.db.SchemaMigrator;
import com.aquasentinel.infrastructure.repository.JdbcAuditRepository;
import com.aquasentinel.infrastructure.repository.JdbcImportBatchRepository;
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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class Milestone5ImportIntegrationTest {

    @TempDir
    Path tempDir;

    private AuthenticationService authService;
    private UserService userService;
    private SourceService sourceService;
    private ImportService importService;
    private ImportBatchRepository batchRepo;
    private SampleRepository sampleRepo;
    private ReadingRepository readingRepo;

    private Session adminSession;
    private Session analystSession;
    private SourceDto source;

    @BeforeEach
    void setUp() {
        Path dbPath = tempDir.resolve("m5-test.db");
        ConnectionFactory connectionFactory = new ConnectionFactory(dbPath);
        SchemaMigrator migrator = new SchemaMigrator(connectionFactory);
        migrator.migrate();

        PBKDF2PasswordHasher hasher = new PBKDF2PasswordHasher();
        JdbcUserRepository userRepo = new JdbcUserRepository(connectionFactory);
        JdbcSourceRepository sourceRepo = new JdbcSourceRepository(connectionFactory);
        JdbcInspectionRepository inspectionRepo = new JdbcInspectionRepository(connectionFactory);
        JdbcParameterRepository paramRepo = new JdbcParameterRepository(connectionFactory);
        sampleRepo = new JdbcSampleRepository(connectionFactory);
        readingRepo = new JdbcReadingRepository(connectionFactory);
        batchRepo = new JdbcImportBatchRepository(connectionFactory);
        JdbcAuditRepository auditRepo = new JdbcAuditRepository(connectionFactory);

        authService = new AuthenticationService(userRepo, hasher, auditRepo);
        userService = new UserService(userRepo, hasher, auditRepo);
        sourceService = new SourceService(sourceRepo, auditRepo);
        importService = new ImportService(batchRepo, sourceRepo, paramRepo, inspectionRepo, sampleRepo, readingRepo, auditRepo);

        adminSession = authService.setupInitialAdmin("admin", "AdminPass123!".toCharArray());
        UserDto analyst = userService.createUser(adminSession, "analyst1", "AnalystPass1!".toCharArray(), Role.ANALYST);
        analystSession = new Session(analyst.id(), analyst.username(), analyst.role());

        source = sourceService.createSource(adminSession, "SRC-DELHI", "Yamuna Bridge",
                SourceType.RIVER, "Irrigation", "Delhi Point 1", 28.6139, 77.2090);
    }

    @Test
    void testValidCsvPreviewAndAtomicCommit() throws Exception {
        String csvContent = """
            source_code,record_code,record_kind,observed_at,period_start,period_end,statistic,parameter_code,value,unit
            SRC-DELHI,SMP-01,POINT_SAMPLE,2026-10-15T08:00:00Z,,,,PH,7.4,1
            SRC-DELHI,SMP-01,POINT_SAMPLE,2026-10-15T08:00:00Z,,,,TURB,3.2,NTU
            SRC-DELHI,SUM-01,PERIOD_SUMMARY,,2025-01-01,2025-12-31,MEAN,DO,6.2,mg/L
        """;
        Path csvFile = tempDir.resolve("valid_import.csv");
        Files.writeString(csvFile, csvContent);

        ImportMetadata meta = new ImportMetadata(
                "Central Water Commission", "Yamuna Annual Dataset", "https://cpcb.nic.in",
                "2026-10-20", "Open Access", "None", RecordOrigin.HISTORICAL_IMPORT
        );

        ImportPreviewDto preview = importService.preview(analystSession, csvFile, meta);
        assertNotNull(preview);
        assertTrue(preview.validToCommit());
        assertEquals(3, preview.totalRows());
        assertEquals(2, preview.pointSampleCount());
        assertEquals(1, preview.periodSummaryCount());
        assertTrue(preview.validationErrors().isEmpty());

        // Commit batch
        ImportReceipt receipt = importService.commit(analystSession, preview.previewId());
        assertNotNull(receipt);
        assertEquals(3, receipt.rowsImported());
        assertEquals(1, receipt.samplesCreated());
        assertEquals(1, receipt.summariesSaved());

        // Verify stored batch in repository
        var storedBatch = batchRepo.findById(receipt.batchId());
        assertTrue(storedBatch.isPresent());
        assertEquals(3, storedBatch.get().getRowCount());

        // Repeated commit on same preview fails
        assertThrows(ValidationException.class, () ->
                importService.commit(analystSession, preview.previewId()));

        // Repeated preview on same file content fails with duplicate hash rejection
        assertThrows(ValidationException.class, () ->
                importService.preview(analystSession, csvFile, meta));
    }

    @Test
    void testInvalidCsvRejectsCommitAndIdentifiesErrors() throws Exception {
        String csvContent = """
            source_code,record_code,record_kind,observed_at,period_start,period_end,statistic,parameter_code,value,unit
            SRC-UNKNOWN,SMP-01,POINT_SAMPLE,2026-10-15T08:00:00Z,,,,PH,7.4,1
            SRC-DELHI,SMP-02,POINT_SAMPLE,2026-10-15T08:00:00Z,,,,UNKNOWN_PARAM,5.0,NTU
            SRC-DELHI,SMP-03,POINT_SAMPLE,not-a-date,,,,PH,7.4,1
            SRC-DELHI,SUM-02,PERIOD_SUMMARY,,2025-12-31,2025-01-01,MEAN,DO,6.2,mg/L
        """;
        Path csvFile = tempDir.resolve("invalid_import.csv");
        Files.writeString(csvFile, csvContent);

        ImportMetadata meta = new ImportMetadata(
                "Test", "Invalid Dataset", "https://test.gov",
                "2026-10-20", "Test", "None", RecordOrigin.HISTORICAL_IMPORT
        );

        ImportPreviewDto preview = importService.preview(analystSession, csvFile, meta);
        assertNotNull(preview);
        assertFalse(preview.validToCommit(), "Errors must prevent commit flag");
        assertEquals(4, preview.validationErrors().size());

        // Unstaged preview cannot be committed
        assertThrows(ValidationException.class, () ->
                importService.commit(analystSession, preview.previewId()));
    }
}
