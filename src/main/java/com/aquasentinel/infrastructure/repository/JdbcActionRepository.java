package com.aquasentinel.infrastructure.repository;

import com.aquasentinel.application.port.ActionRepository;
import com.aquasentinel.domain.enums.ActionState;
import com.aquasentinel.domain.model.CorrectiveAction;
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

public class JdbcActionRepository implements ActionRepository {
    private final ConnectionFactory connectionFactory;

    public JdbcActionRepository(ConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    @Override
    public Optional<CorrectiveAction> findById(UUID id) {
        String sql = "SELECT * FROM corrective_actions WHERE id = ?";
        try (Connection conn = connectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error finding corrective action by id: " + id, e);
        }
        return Optional.empty();
    }

    @Override
    public List<CorrectiveAction> findByAlertId(UUID alertId) {
        String sql = "SELECT * FROM corrective_actions WHERE alert_id = ? ORDER BY created_at DESC";
        List<CorrectiveAction> list = new ArrayList<>();
        try (Connection conn = connectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, alertId.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error finding actions by alertId", e);
        }
        return list;
    }

    @Override
    public List<CorrectiveAction> findByAssignedUserId(UUID assignedUserId) {
        String sql = "SELECT * FROM corrective_actions WHERE assigned_user_id = ? ORDER BY due_date ASC";
        List<CorrectiveAction> list = new ArrayList<>();
        try (Connection conn = connectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, assignedUserId.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error finding actions by assigned user", e);
        }
        return list;
    }

    @Override
    public List<CorrectiveAction> findByState(ActionState state) {
        String sql = "SELECT * FROM corrective_actions WHERE state = ? ORDER BY due_date ASC";
        List<CorrectiveAction> list = new ArrayList<>();
        try (Connection conn = connectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, state.name());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error finding actions by state", e);
        }
        return list;
    }

    @Override
    public List<CorrectiveAction> findAll(int offset, int limit) {
        String sql = "SELECT * FROM corrective_actions ORDER BY created_at DESC LIMIT ? OFFSET ?";
        List<CorrectiveAction> list = new ArrayList<>();
        try (Connection conn = connectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            ps.setInt(2, offset);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error finding corrective actions paginated", e);
        }
        return list;
    }

    @Override
    public long count() {
        String sql = "SELECT count(*) FROM corrective_actions";
        try (Connection conn = connectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) return rs.getLong(1);
        } catch (SQLException e) {
            throw new RuntimeException("Error counting corrective actions", e);
        }
        return 0;
    }

    @Override
    public void save(CorrectiveAction a) {
        String sql = """
            INSERT INTO corrective_actions (id, alert_id, assigned_user_id, description, due_date,
                                           state, completion_notes, followup_evaluation_id, closed_at,
                                           created_at, version)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
        try (Connection conn = connectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, a.getId().toString());
            ps.setString(2, a.getAlertId().toString());
            ps.setString(3, a.getAssignedUserId().toString());
            ps.setString(4, a.getDescription());
            ps.setString(5, a.getDueDate().toString());
            ps.setString(6, a.getState().name());
            ps.setString(7, a.getCompletionNotes());
            ps.setString(8, a.getFollowupEvaluationId() != null ? a.getFollowupEvaluationId().toString() : null);
            ps.setString(9, a.getClosedAt() != null ? a.getClosedAt().toString() : null);
            ps.setString(10, a.getCreatedAt().toString());
            ps.setInt(11, a.getVersion());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error saving corrective action: " + a.getId(), e);
        }
    }

    @Override
    public void update(CorrectiveAction a) {
        String sql = """
            UPDATE corrective_actions SET state = ?, completion_notes = ?, followup_evaluation_id = ?,
                                         closed_at = ?, version = version + 1
            WHERE id = ? AND version = ?
        """;
        try (Connection conn = connectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, a.getState().name());
            ps.setString(2, a.getCompletionNotes());
            ps.setString(3, a.getFollowupEvaluationId() != null ? a.getFollowupEvaluationId().toString() : null);
            ps.setString(4, a.getClosedAt() != null ? a.getClosedAt().toString() : null);
            ps.setString(5, a.getId().toString());
            ps.setInt(6, a.getVersion());
            int affected = ps.executeUpdate();
            if (affected == 0) {
                throw new IllegalStateException("Optimistic locking failure updating action " + a.getId());
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error updating corrective action " + a.getId(), e);
        }
    }

    private CorrectiveAction mapRow(ResultSet rs) throws SQLException {
        String compNotes = rs.getString("completion_notes");
        String followEval = rs.getString("followup_evaluation_id");
        String closedAtStr = rs.getString("closed_at");

        return new CorrectiveAction(
                UUID.fromString(rs.getString("id")),
                UUID.fromString(rs.getString("alert_id")),
                UUID.fromString(rs.getString("assigned_user_id")),
                rs.getString("description"),
                LocalDate.parse(rs.getString("due_date")),
                ActionState.valueOf(rs.getString("state")),
                compNotes,
                followEval != null ? UUID.fromString(followEval) : null,
                closedAtStr != null ? Instant.parse(closedAtStr) : null,
                Instant.parse(rs.getString("created_at")),
                rs.getInt("version")
        );
    }
}
