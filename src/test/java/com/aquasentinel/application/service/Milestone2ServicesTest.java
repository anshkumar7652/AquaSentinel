package com.aquasentinel.application.service;

import com.aquasentinel.application.dto.InspectionDto;
import com.aquasentinel.application.dto.SourceDto;
import com.aquasentinel.application.dto.UserDto;
import com.aquasentinel.application.security.Session;
import com.aquasentinel.domain.enums.InspectionStatus;
import com.aquasentinel.domain.enums.Role;
import com.aquasentinel.domain.enums.SourceType;
import com.aquasentinel.domain.exception.AuthenticationException;
import com.aquasentinel.domain.exception.AuthorizationException;
import com.aquasentinel.domain.exception.ValidationException;
import com.aquasentinel.infrastructure.db.ConnectionFactory;
import com.aquasentinel.infrastructure.db.SchemaMigrator;
import com.aquasentinel.infrastructure.repository.JdbcAuditRepository;
import com.aquasentinel.infrastructure.repository.JdbcInspectionRepository;
import com.aquasentinel.infrastructure.repository.JdbcSourceRepository;
import com.aquasentinel.infrastructure.repository.JdbcUserRepository;
import com.aquasentinel.infrastructure.security.PBKDF2PasswordHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class Milestone2ServicesTest {

    @TempDir
    Path tempDir;

    private AuthenticationService authService;
    private UserService userService;
    private SourceService sourceService;
    private InspectionService inspectionService;

    @BeforeEach
    void setUp() {
        Path dbPath = tempDir.resolve("m2-test.db");
        ConnectionFactory connectionFactory = new ConnectionFactory(dbPath);
        SchemaMigrator migrator = new SchemaMigrator(connectionFactory);
        migrator.migrate();

        PBKDF2PasswordHasher hasher = new PBKDF2PasswordHasher();
        JdbcUserRepository userRepo = new JdbcUserRepository(connectionFactory);
        JdbcSourceRepository sourceRepo = new JdbcSourceRepository(connectionFactory);
        JdbcInspectionRepository inspectionRepo = new JdbcInspectionRepository(connectionFactory);
        JdbcAuditRepository auditRepo = new JdbcAuditRepository(connectionFactory);

        authService = new AuthenticationService(userRepo, hasher, auditRepo);
        userService = new UserService(userRepo, hasher, auditRepo);
        sourceService = new SourceService(sourceRepo, auditRepo);
        inspectionService = new InspectionService(inspectionRepo, sourceRepo, userRepo, auditRepo);
    }

    @Test
    void testFirstRunSetupAndAuthentication() {
        assertTrue(authService.isSetupRequired());

        Session adminSession = authService.setupInitialAdmin("superadmin", "Password123!".toCharArray());
        assertNotNull(adminSession);
        assertEquals("superadmin", adminSession.username());
        assertEquals(Role.ADMINISTRATOR, adminSession.role());

        assertFalse(authService.isSetupRequired());

        // Repeated first run setup should fail
        assertThrows(ValidationException.class, () ->
                authService.setupInitialAdmin("anotherAdmin", "Password123!".toCharArray()));

        // Login with correct password
        Session loggedIn = authService.authenticate("superadmin", "Password123!".toCharArray());
        assertNotNull(loggedIn);
        assertEquals(adminSession.userId(), loggedIn.userId());

        // Login with wrong password
        assertThrows(AuthenticationException.class, () ->
                authService.authenticate("superadmin", "WrongPassword!".toCharArray()));

        // Login with non-existing user
        assertThrows(AuthenticationException.class, () ->
                authService.authenticate("ghostUser", "AnyPassword!".toCharArray()));
    }

    @Test
    void testUserManagementAndRBAC() {
        Session adminSession = authService.setupInitialAdmin("admin", "AdminPass123!".toCharArray());

        // Admin creates field officer
        UserDto officer = userService.createUser(adminSession, "officer1", "OfficerPass1!".toCharArray(), Role.FIELD_OFFICER);
        assertNotNull(officer);
        assertEquals(Role.FIELD_OFFICER, officer.role());

        // Field officer authenticates
        Session officerSession = authService.authenticate("officer1", "OfficerPass1!".toCharArray());
        assertEquals(Role.FIELD_OFFICER, officerSession.role());

        // Field officer cannot create other users
        assertThrows(AuthorizationException.class, () ->
                userService.createUser(officerSession, "analyst1", "AnalystPass1!".toCharArray(), Role.ANALYST));

        // Deactivating user prevents login
        userService.setUserActiveStatus(adminSession, officer.id(), false);
        assertThrows(AuthenticationException.class, () ->
                authService.authenticate("officer1", "OfficerPass1!".toCharArray()));

        // Admin cannot deactivate self
        assertThrows(ValidationException.class, () ->
                userService.setUserActiveStatus(adminSession, adminSession.userId(), false));
    }

    @Test
    void testSourceManagementAndValidations() {
        Session adminSession = authService.setupInitialAdmin("admin", "AdminPass123!".toCharArray());
        UserDto viewer = userService.createUser(adminSession, "viewer1", "ViewerPass1!".toCharArray(), Role.VIEWER);
        Session viewerSession = new Session(viewer.id(), viewer.username(), viewer.role());

        // Viewer cannot create sources
        assertThrows(AuthorizationException.class, () ->
                sourceService.createSource(viewerSession, "SRC-01", "Yamuna River", SourceType.RIVER,
                        "Irrigation", "Delhi Point A", 28.6139, 77.2090));

        // Admin creates source
        SourceDto source = sourceService.createSource(adminSession, "SRC-01", "Yamuna River", SourceType.RIVER,
                "Irrigation", "Delhi Point A", 28.6139, 77.2090);
        assertNotNull(source);
        assertEquals("SRC-01", source.code());

        // Duplicate code fails
        assertThrows(ValidationException.class, () ->
                sourceService.createSource(adminSession, "SRC-01", "Duplicate Code", SourceType.LAKE,
                        "Drinking", "Location B", null, null));

        // Invalid coordinates fail
        assertThrows(ValidationException.class, () ->
                sourceService.createSource(adminSession, "SRC-02", "Bad Coords", SourceType.RIVER,
                        "Testing", "Location C", 95.0, 77.0));

        // Archive source
        sourceService.archiveSource(adminSession, source.id());
        List<SourceDto> active = sourceService.listActiveSources(viewerSession);
        assertTrue(active.isEmpty(), "Archived source should not appear in active sources");
    }

    @Test
    void testInspectionSchedulingAndTransitions() {
        Session adminSession = authService.setupInitialAdmin("admin", "AdminPass123!".toCharArray());
        UserDto officer = userService.createUser(adminSession, "officer2", "OfficerPass2!".toCharArray(), Role.FIELD_OFFICER);
        SourceDto source = sourceService.createSource(adminSession, "SRC-10", "Ganga Basin", SourceType.RIVER,
                "Bathing", "Varanasi", 25.3176, 82.9739);

        // Schedule inspection
        InspectionDto inspection = inspectionService.scheduleInspection(
                adminSession, source.id(), officer.id(), LocalDate.now().plusDays(3), "Routine sampling");
        assertNotNull(inspection);
        assertEquals(InspectionStatus.SCHEDULED, inspection.status());

        // Assigned officer can update status to IN_PROGRESS
        Session officerSession = new Session(officer.id(), officer.username(), officer.role());
        assertDoesNotThrow(() ->
                inspectionService.updateStatus(officerSession, inspection.id(), InspectionStatus.IN_PROGRESS, null));

        // Cannot schedule on an archived source
        sourceService.archiveSource(adminSession, source.id());
        assertThrows(ValidationException.class, () ->
                inspectionService.scheduleInspection(adminSession, source.id(), officer.id(), LocalDate.now().plusDays(5), "Invalid"));
    }
}
