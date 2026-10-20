package com.aquasentinel.infrastructure.db;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sqlite.SQLiteConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * SQLite ConnectionFactory ensuring WAL mode, foreign keys, and busy timeout on every connection.
 */
public class ConnectionFactory {
    private static final Logger log = LoggerFactory.getLogger(ConnectionFactory.class);

    private final String jdbcUrl;
    private final Path dbPath;

    public ConnectionFactory(Path dbPath) {
        this.dbPath = dbPath;
        if (dbPath != null) {
            try {
                if (dbPath.getParent() != null && !Files.exists(dbPath.getParent())) {
                    Files.createDirectories(dbPath.getParent());
                }
            } catch (IOException e) {
                log.error("Failed to create parent directories for SQLite db at {}", dbPath, e);
            }
            this.jdbcUrl = "jdbc:sqlite:" + dbPath.toAbsolutePath().toString().replace('\\', '/');
        } else {
            this.jdbcUrl = "jdbc:sqlite::memory:";
        }
    }

    public Connection getConnection() throws SQLException {
        SQLiteConfig config = new SQLiteConfig();
        config.enforceForeignKeys(true);
        config.setBusyTimeout(5000);
        config.setJournalMode(SQLiteConfig.JournalMode.WAL);
        config.setSynchronous(SQLiteConfig.SynchronousMode.NORMAL);

        Connection connection = DriverManager.getConnection(jdbcUrl, config.toProperties());
        return connection;
    }

    public Path getDbPath() {
        return dbPath;
    }

    public String getJdbcUrl() {
        return jdbcUrl;
    }
}
