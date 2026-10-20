package com.aquasentinel.infrastructure.repository;

import com.aquasentinel.application.port.AlertRepository;
import com.aquasentinel.domain.enums.AlertState;
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

public class JdbcAlertRepository implements AlertRepository {
    private final ConnectionFactory connectionFactory;

    public JdbcAlertRepository(ConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    @Override
    public Optional<StoredAlert> findById(UUID id) {
        String sql = "SELECT * FROM alerts WHERE id = ?";
        try (Connection conn = connectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error finding alert by id: " + id, e);
        }
        return Optional.empty();
    }

    @Override
    public Optional<StoredAlert> findByEvaluationId(UUID evaluationId) {
        String sql = "SELECT * FROM alerts WHERE evaluation_id = ?";
        try (Connection conn = connectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, evaluationId.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error finding alert by evaluationId: " + evaluationId, e);
        }
        return Optional.empty();
    }

    @Override
    public List<StoredAlert> findByState(AlertState state) {
        String sql = "SELECT * FROM alerts WHERE state = ? ORDER BY id ASC";
        List<StoredAlert> list = new ArrayList<>();
        try (Connection conn = connectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, state.name());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error finding alerts by state", e);
        }
        return list;
    }

    @Override
    public long countUnresolved() {
        String sql = "SELECT count(*) FROM alerts WHERE state != 'RESOLVED'";
        try (Connection conn = connectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) return rs.getLong(1);
        } catch (SQLException e) {
            throw new RuntimeException("Error counting unresolved alerts", e);
        }
        return 0;
    }

    @Override
    public void save(StoredAlert a) {
        String sql = """
            INSERT INTO alerts (id, evaluation_id, state, acknowledged_by, acknowledged_at, resolved_at, version)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        """;
        try (Connection conn = connectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, a.id().toString());
            ps.setString(2, a.evaluationId().toString());
            ps.setString(3, a.state().name());
            ps.setString(4, a.acknowledgedBy() != null ? a.acknowledgedBy().toString() : null);
            ps.setString(5, a.acknowledgedAt() != null ? a.acknowledgedAt().toString() : null);
            ps.setString(6, a.resolvedAt() != null ? a.resolvedAt().toString() : null);
            ps.setInt(7, a.version());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error saving alert: " + a.id(), e);
        }
    }

    @Override
    public void update(StoredAlert a) {
        String sql = """
            UPDATE alerts SET state = ?, acknowledged_by = ?, acknowledged_at = ?, resolved_at = ?, version = version + 1
            WHERE id = ? AND version = ?
        """;
        try (Connection conn = connectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, a.state().name());
            ps.setString(2, a.acknowledgedBy() != null ? a.acknowledgedBy().toString() : null);
            ps.setString(3, a.acknowledgedAt() != null ? a.acknowledgedAt().toString() : null);
            ps.setString(4, a.resolvedAt() != null ? a.resolvedAt().toString() : null);
            ps.setString(5, a.id().toString());
            ps.setInt(6, a.version());
            int affected = ps.executeUpdate();
            if (affected == 0) {
                throw new IllegalStateException("Optimistic locking failure updating alert " + a.id());
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error updating alert: " + a.id(), e);
        }
    }

    private StoredAlert mapRow(ResultSet rs) throws SQLException {
        String ackByStr = rs.getString("acknowledged_by");
        String ackAtStr = rs.getString("acknowledged_at");
        String resAtStr = rs.getString("resolved_at");
        return new StoredAlert(
                UUID.fromString(rs.getString("id")),
                UUID.fromString(rs.getString("evaluation_id")),
                AlertState.valueOf(rs.getString("state")),
                ackByStr != null ? UUID.fromString(ackByStr) : null,
                ackAtStr != null ? Instant.parse(ackAtStr) : null,
                resAtStr != null ? Instant.parse(resAtStr) : null,
                rs.getInt("version")
        );
    }
}
