package com.aquasentinel.infrastructure.repository;

import com.aquasentinel.application.port.ImportBatchRepository;
import com.aquasentinel.domain.enums.RecordOrigin;
import com.aquasentinel.domain.enums.SummaryStatistic;
import com.aquasentinel.domain.model.HistoricalObservation;
import com.aquasentinel.domain.model.ImportBatch;
import com.aquasentinel.infrastructure.db.ConnectionFactory;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class JdbcImportBatchRepository implements ImportBatchRepository {
    private final ConnectionFactory connectionFactory;

    public JdbcImportBatchRepository(ConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    @Override
    public Optional<ImportBatch> findById(UUID id) {
        String sql = "SELECT * FROM import_batches WHERE id = ?";
        try (Connection conn = connectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapBatch(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error finding import batch by id", e);
        }
        return Optional.empty();
    }

    @Override
    public Optional<ImportBatch> findByFileSha256(String fileSha256) {
        String sql = "SELECT * FROM import_batches WHERE file_sha256 = ?";
        try (Connection conn = connectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, fileSha256);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapBatch(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error finding import batch by hash", e);
        }
        return Optional.empty();
    }

    @Override
    public List<ImportBatch> findAll() {
        String sql = "SELECT * FROM import_batches ORDER BY imported_at DESC";
        List<ImportBatch> list = new ArrayList<>();
        try (Connection conn = connectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(mapBatch(rs));
        } catch (SQLException e) {
            throw new RuntimeException("Error finding all import batches", e);
        }
        return list;
    }

    @Override
    public void save(ImportBatch b) {
        String sql = """
            INSERT INTO import_batches (id, file_sha256, filename, publisher, dataset_title, source_url,
                                       accessed_on, license_status, transformations, provenance_kind,
                                       imported_by, imported_at, row_count)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
        try (Connection conn = connectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, b.getId().toString());
            ps.setString(2, b.getFileSha256());
            ps.setString(3, b.getFilename());
            ps.setString(4, b.getPublisher());
            ps.setString(5, b.getDatasetTitle());
            ps.setString(6, b.getSourceUrl());
            ps.setString(7, b.getAccessedOn());
            ps.setString(8, b.getLicenseStatus());
            ps.setString(9, b.getTransformations());
            ps.setString(10, b.getProvenanceKind().name());
            ps.setString(11, b.getImportedBy().toString());
            ps.setString(12, b.getImportedAt().toString());
            ps.setInt(13, b.getRowCount());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error saving import batch: " + b.getFilename(), e);
        }
    }

    @Override
    public void saveObservations(List<HistoricalObservation> observations) {
        String sql = """
            INSERT INTO historical_observations (id, source_id, parameter_id, period_start, period_end,
                                                 statistic, value_decimal, unit, import_batch_id, original_row)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
        try (Connection conn = connectionFactory.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (HistoricalObservation o : observations) {
                    ps.setString(1, o.getId().toString());
                    ps.setString(2, o.getSourceId().toString());
                    ps.setString(3, o.getParameterId().toString());
                    ps.setString(4, o.getPeriodStart().toString());
                    ps.setString(5, o.getPeriodEnd().toString());
                    ps.setString(6, o.getStatistic().name());
                    ps.setString(7, o.getValueDecimal().toPlainString());
                    ps.setString(8, o.getUnit());
                    ps.setString(9, o.getImportBatchId().toString());
                    ps.setInt(10, o.getOriginalRow());
                    ps.addBatch();
                }
                ps.executeBatch();
                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw new RuntimeException("Failed to batch insert historical observations", e);
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Database error saving observations", e);
        }
    }

    private ImportBatch mapBatch(ResultSet rs) throws SQLException {
        return new ImportBatch(
                UUID.fromString(rs.getString("id")),
                rs.getString("file_sha256"),
                rs.getString("filename"),
                rs.getString("publisher"),
                rs.getString("dataset_title"),
                rs.getString("source_url"),
                rs.getString("accessed_on"),
                rs.getString("license_status"),
                rs.getString("transformations"),
                RecordOrigin.valueOf(rs.getString("provenance_kind")),
                UUID.fromString(rs.getString("imported_by")),
                Instant.parse(rs.getString("imported_at")),
                rs.getInt("row_count")
        );
    }
}
