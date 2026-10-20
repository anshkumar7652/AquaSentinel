package com.aquasentinel.infrastructure.repository;

import com.aquasentinel.application.port.SampleRepository;
import com.aquasentinel.domain.enums.RecordOrigin;
import com.aquasentinel.domain.enums.SampleStatus;
import com.aquasentinel.domain.model.WaterSample;
import com.aquasentinel.infrastructure.db.ConnectionFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class JdbcSampleRepository implements SampleRepository {
    private final ConnectionFactory connectionFactory;

    public JdbcSampleRepository(ConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    @Override
    public Optional<WaterSample> findById(UUID id) {
        String sql = "SELECT * FROM samples WHERE id = ?";
        try (Connection conn = connectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error finding sample by id: " + id, e);
        }
        return Optional.empty();
    }

    @Override
    public Optional<WaterSample> findByCode(String code) {
        String sql = "SELECT * FROM samples WHERE code = ?";
        try (Connection conn = connectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, code);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error finding sample by code: " + code, e);
        }
        return Optional.empty();
    }

    @Override
    public List<WaterSample> findByInspectionId(UUID inspectionId) {
        String sql = "SELECT * FROM samples WHERE inspection_id = ? ORDER BY created_at DESC";
        List<WaterSample> list = new ArrayList<>();
        try (Connection conn = connectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, inspectionId.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error finding samples by inspectionId: " + inspectionId, e);
        }
        return list;
    }

    @Override
    public List<WaterSample> findAll(int offset, int limit) {
        String sql = "SELECT * FROM samples ORDER BY created_at DESC LIMIT ? OFFSET ?";
        List<WaterSample> list = new ArrayList<>();
        try (Connection conn = connectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            ps.setInt(2, offset);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error finding samples paginated", e);
        }
        return list;
    }

    @Override
    public long count() {
        String sql = "SELECT count(*) FROM samples";
        try (Connection conn = connectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) return rs.getLong(1);
        } catch (SQLException e) {
            throw new RuntimeException("Error counting samples", e);
        }
        return 0;
    }

    @Override
    public void save(WaterSample s) {
        String sql = """
            INSERT INTO samples (id, code, inspection_id, status, collected_at, received_at, collected_by,
                                 origin, import_batch_id, supersedes_sample_id, created_at, version)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
        try (Connection conn = connectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, s.getId().toString());
            ps.setString(2, s.getCode());
            ps.setString(3, s.getInspectionId().toString());
            ps.setString(4, s.getStatus().name());
            ps.setString(5, s.getCollectedAt() != null ? s.getCollectedAt().toString() : null);
            ps.setString(6, s.getReceivedAt() != null ? s.getReceivedAt().toString() : null);
            ps.setString(7, s.getCollectedBy() != null ? s.getCollectedBy().toString() : null);
            ps.setString(8, s.getOrigin().name());
            ps.setString(9, s.getImportBatchId() != null ? s.getImportBatchId().toString() : null);
            ps.setString(10, s.getSupersedesSampleId() != null ? s.getSupersedesSampleId().toString() : null);
            ps.setString(11, s.getCreatedAt().toString());
            ps.setInt(12, s.getVersion());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error saving sample: " + s.getCode(), e);
        }
    }

    @Override
    public void update(WaterSample s) {
        String sql = """
            UPDATE samples SET status = ?, collected_at = ?, received_at = ?, collected_by = ?,
                               supersedes_sample_id = ?, version = version + 1
            WHERE id = ? AND version = ?
        """;
        try (Connection conn = connectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, s.getStatus().name());
            ps.setString(2, s.getCollectedAt() != null ? s.getCollectedAt().toString() : null);
            ps.setString(3, s.getReceivedAt() != null ? s.getReceivedAt().toString() : null);
            ps.setString(4, s.getCollectedBy() != null ? s.getCollectedBy().toString() : null);
            ps.setString(5, s.getSupersedesSampleId() != null ? s.getSupersedesSampleId().toString() : null);
            ps.setString(6, s.getId().toString());
            ps.setInt(7, s.getVersion());
            int affected = ps.executeUpdate();
            if (affected == 0) {
                throw new IllegalStateException("Optimistic locking failure updating sample " + s.getId());
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error updating sample: " + s.getId(), e);
        }
    }

    private WaterSample mapRow(ResultSet rs) throws SQLException {
        String collectedStr = rs.getString("collected_at");
        String receivedStr = rs.getString("received_at");
        String collectorStr = rs.getString("collected_by");
        String batchStr = rs.getString("import_batch_id");
        String supersedesStr = rs.getString("supersedes_sample_id");

        return new WaterSample(
                UUID.fromString(rs.getString("id")),
                rs.getString("code"),
                UUID.fromString(rs.getString("inspection_id")),
                SampleStatus.valueOf(rs.getString("status")),
                collectedStr != null ? Instant.parse(collectedStr) : null,
                receivedStr != null ? Instant.parse(receivedStr) : null,
                collectorStr != null ? UUID.fromString(collectorStr) : null,
                RecordOrigin.valueOf(rs.getString("origin")),
                batchStr != null ? UUID.fromString(batchStr) : null,
                supersedesStr != null ? UUID.fromString(supersedesStr) : null,
                Instant.parse(rs.getString("created_at")),
                rs.getInt("version")
        );
    }
}
