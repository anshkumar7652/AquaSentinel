package com.aquasentinel.infrastructure.repository;

import com.aquasentinel.application.port.AuditRepository;
import com.aquasentinel.infrastructure.db.ConnectionFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;

public class JdbcAuditRepository implements AuditRepository {
    private final ConnectionFactory connectionFactory;

    public JdbcAuditRepository(ConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    @Override
    public void record(UUID actorId, String action, String entityType, String entityId,
                       Instant occurredAt, String beforeJson, String afterJson) {
        String sql = """
            INSERT INTO audit_events (id, actor_id, action, entity_type, entity_id, occurred_at, before_json, after_json)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """;
        try (Connection conn = connectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, UUID.randomUUID().toString());
            if (actorId != null) ps.setString(2, actorId.toString()); else ps.setNull(2, java.sql.Types.VARCHAR);
            ps.setString(3, action);
            ps.setString(4, entityType);
            ps.setString(5, entityId);
            ps.setString(6, occurredAt.toString());
            ps.setString(7, beforeJson);
            ps.setString(8, afterJson);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error writing audit record", e);
        }
    }
}
