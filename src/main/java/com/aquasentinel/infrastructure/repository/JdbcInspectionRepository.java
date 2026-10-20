package com.aquasentinel.infrastructure.repository;

import com.aquasentinel.application.port.InspectionRepository;
import com.aquasentinel.domain.enums.InspectionStatus;
import com.aquasentinel.domain.model.Inspection;
import com.aquasentinel.infrastructure.db.ConnectionFactory;

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

public class JdbcInspectionRepository implements InspectionRepository {
    private final ConnectionFactory connectionFactory;

    public JdbcInspectionRepository(ConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    @Override
    public Optional<Inspection> findById(UUID id) {
        String sql = "SELECT * FROM inspections WHERE id = ?";
        try (Connection conn = connectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error finding inspection by id: " + id, e);
        }
        return Optional.empty();
    }

    @Override
    public List<Inspection> findBySourceId(UUID sourceId) {
        String sql = "SELECT * FROM inspections WHERE source_id = ? ORDER BY due_date DESC";
        List<Inspection> list = new ArrayList<>();
        try (Connection conn = connectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, sourceId.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error finding inspections by sourceId: " + sourceId, e);
        }
        return list;
    }

    @Override
    public List<Inspection> findByAssignedUserId(UUID assignedUserId) {
        String sql = "SELECT * FROM inspections WHERE assigned_user_id = ? ORDER BY due_date DESC";
        List<Inspection> list = new ArrayList<>();
        try (Connection conn = connectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, assignedUserId.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error finding inspections by assignedUserId: " + assignedUserId, e);
        }
        return list;
    }

    @Override
    public List<Inspection> findAll(int offset, int limit) {
        String sql = "SELECT * FROM inspections ORDER BY due_date DESC LIMIT ? OFFSET ?";
        List<Inspection> list = new ArrayList<>();
        try (Connection conn = connectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            ps.setInt(2, offset);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error finding inspections paginated", e);
        }
        return list;
    }

    @Override
    public long count() {
        String sql = "SELECT count(*) FROM inspections";
        try (Connection conn = connectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error counting inspections", e);
        }
        return 0;
    }

    @Override
    public void save(Inspection inspection) {
        String sql = """
            INSERT INTO inspections (id, source_id, assigned_user_id, due_date, status, no_sample_reason, notes, created_at, version)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
        try (Connection conn = connectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, inspection.getId().toString());
            ps.setString(2, inspection.getSourceId().toString());
            ps.setString(3, inspection.getAssignedUserId().toString());
            ps.setString(4, inspection.getDueDate().toString());
            ps.setString(5, inspection.getStatus().name());
            ps.setString(6, inspection.getNoSampleReason());
            ps.setString(7, inspection.getNotes());
            ps.setString(8, inspection.getCreatedAt().toString());
            ps.setInt(9, inspection.getVersion());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error saving inspection: " + inspection.getId(), e);
        }
    }

    @Override
    public void update(Inspection inspection) {
        String sql = """
            UPDATE inspections SET assigned_user_id = ?, due_date = ?, status = ?,
                                  no_sample_reason = ?, notes = ?, version = version + 1
            WHERE id = ? AND version = ?
        """;
        try (Connection conn = connectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, inspection.getAssignedUserId().toString());
            ps.setString(2, inspection.getDueDate().toString());
            ps.setString(3, inspection.getStatus().name());
            ps.setString(4, inspection.getNoSampleReason());
            ps.setString(5, inspection.getNotes());
            ps.setString(6, inspection.getId().toString());
            ps.setInt(7, inspection.getVersion());
            int affected = ps.executeUpdate();
            if (affected == 0) {
                throw new IllegalStateException("Optimistic locking failure updating inspection " + inspection.getId());
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error updating inspection: " + inspection.getId(), e);
        }
    }

    private Inspection mapRow(ResultSet rs) throws SQLException {
        return new Inspection(
                UUID.fromString(rs.getString("id")),
                UUID.fromString(rs.getString("source_id")),
                UUID.fromString(rs.getString("assigned_user_id")),
                LocalDate.parse(rs.getString("due_date")),
                InspectionStatus.valueOf(rs.getString("status")),
                rs.getString("no_sample_reason"),
                rs.getString("notes"),
                Instant.parse(rs.getString("created_at")),
                rs.getInt("version")
        );
    }
}
