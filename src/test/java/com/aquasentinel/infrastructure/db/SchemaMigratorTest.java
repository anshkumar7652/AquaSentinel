package com.aquasentinel.infrastructure.db;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

class SchemaMigratorTest {

    @TempDir
    Path tempDir;

    private ConnectionFactory connectionFactory;
    private SchemaMigrator migrator;

    @BeforeEach
    void setUp() {
        Path dbPath = tempDir.resolve("test-aquasentinel.db");
        connectionFactory = new ConnectionFactory(dbPath);
        migrator = new SchemaMigrator(connectionFactory);
    }

    @Test
    void testInitialMigrationSucceedsAndAppliesTables() throws Exception {
        migrator.migrate();

        try (Connection conn = connectionFactory.getConnection();
             Statement stmt = conn.createStatement()) {

            // Verify foreign keys are ON
            try (ResultSet rs = stmt.executeQuery("PRAGMA foreign_keys")) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1), "Foreign keys must be enabled");
            }

            // Verify WAL mode
            try (ResultSet rs = stmt.executeQuery("PRAGMA journal_mode")) {
                assertTrue(rs.next());
                assertEquals("wal", rs.getString(1).toLowerCase(), "Journal mode must be WAL");
            }

            // Verify schema_migrations ledger
            try (ResultSet rs = stmt.executeQuery("SELECT version, checksum FROM schema_migrations WHERE version = 1")) {
                assertTrue(rs.next(), "V1 migration should be recorded in ledger");
                assertNotNull(rs.getString("checksum"));
            }

            // Verify core tables exist
            String[] expectedTables = {
                "users", "sources", "inspections", "samples", "readings",
                "parameters", "profile_versions", "profile_rules", "evaluations",
                "rule_results", "alerts", "corrective_actions", "import_batches",
                "historical_observations", "audit_events"
            };

            for (String table : expectedTables) {
                try (ResultSet rs = stmt.executeQuery("SELECT count(*) FROM " + table)) {
                    assertTrue(rs.next());
                    int expectedCount = switch (table) {
                        case "parameters" -> 7;
                        case "profile_versions" -> 1;
                        case "profile_rules" -> 4;
                        default -> 0;
                    };
                    assertEquals(expectedCount, rs.getInt(1), "Table " + table + " should have expected count");
                }
            }
        }
    }

    @Test
    void testMigrationIsIdempotent() {
        assertDoesNotThrow(() -> migrator.migrate(), "First migration should succeed");
        assertDoesNotThrow(() -> migrator.migrate(), "Subsequent migration on identical DB should be idempotent");
    }
}
