package com.aquasentinel.infrastructure.db;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.HexFormat;

/**
 * Migration manager supporting ordered SQL migrations and checksum validation.
 */
public class SchemaMigrator {
    private static final Logger log = LoggerFactory.getLogger(SchemaMigrator.class);

    private final ConnectionFactory connectionFactory;

    public SchemaMigrator(ConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    public void migrate() {
        try (Connection conn = connectionFactory.getConnection()) {
            conn.setAutoCommit(false);
            try {
                ensureMigrationsTable(conn);
                applyMigration(conn, 1, "/db/migration/V001__initial.sql");
                applyMigration(conn, 2, "/db/migration/V002__seed_parameters.sql");
                applyMigration(conn, 3, "/db/migration/V003__seed_demo_profile.sql");
                conn.commit();
                log.info("Database migrations successfully verified and up to date.");
            } catch (Exception e) {
                conn.rollback();
                throw new IllegalStateException("Failed to run database migrations", e);
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not acquire connection for database migration", e);
        }
    }

    private void ensureMigrationsTable(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS schema_migrations (
                    version INTEGER PRIMARY KEY,
                    checksum TEXT NOT NULL,
                    applied_at TEXT NOT NULL
                );
            """);
        }
    }

    private void applyMigration(Connection conn, int version, String resourcePath) throws Exception {
        String sql = loadResource(resourcePath);
        String checksum = computeSha256(sql);

        try (PreparedStatement ps = conn.prepareStatement("SELECT checksum FROM schema_migrations WHERE version = ?")) {
            ps.setInt(1, version);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String existingChecksum = rs.getString("checksum");
                    if (!existingChecksum.equalsIgnoreCase(checksum)) {
                        throw new IllegalStateException("Migration checksum drift detected for version " + version
                                + ". Expected: " + existingChecksum + ", found: " + checksum);
                    }
                    log.debug("Migration V{} already applied with matching checksum.", version);
                    return;
                }
            }
        }

        log.info("Applying migration version {} from {}", version, resourcePath);
        executeSqlScript(conn, sql);

        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO schema_migrations (version, checksum, applied_at) VALUES (?, ?, ?)")) {
            ps.setInt(1, version);
            ps.setString(2, checksum);
            ps.setString(3, Instant.now().toString());
            ps.executeUpdate();
        }
    }

    private void executeSqlScript(Connection conn, String script) throws SQLException {
        // Strip block comments
        String sanitized = script.replaceAll("/\\*.*?\\*/", "");

        StringBuilder currentStatement = new StringBuilder();
        for (String line : sanitized.split("\r?\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("--") || trimmed.isEmpty()) {
                continue;
            }
            currentStatement.append(line).append("\n");
            if (trimmed.endsWith(";")) {
                String stmtStr = currentStatement.toString().trim();
                if (!stmtStr.isEmpty()) {
                    try (Statement stmt = conn.createStatement()) {
                        stmt.execute(stmtStr);
                    }
                }
                currentStatement.setLength(0);
            }
        }
        String remaining = currentStatement.toString().trim();
        if (!remaining.isEmpty()) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute(remaining);
            }
        }
    }

    private String loadResource(String path) throws Exception {
        try (InputStream in = getClass().getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalArgumentException("Resource not found: " + path);
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                }
                return sb.toString();
            }
        }
    }

    private String computeSha256(String input) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hash);
    }
}
