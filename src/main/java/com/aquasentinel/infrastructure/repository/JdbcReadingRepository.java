package com.aquasentinel.infrastructure.repository;

import com.aquasentinel.application.port.ReadingRepository;
import com.aquasentinel.domain.enums.RecordOrigin;
import com.aquasentinel.domain.model.ParameterReading;
import com.aquasentinel.infrastructure.db.ConnectionFactory;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class JdbcReadingRepository implements ReadingRepository {
    private final ConnectionFactory connectionFactory;

    public JdbcReadingRepository(ConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    @Override
    public List<ParameterReading> findBySampleId(UUID sampleId) {
        String sql = "SELECT * FROM readings WHERE sample_id = ? ORDER BY entered_at ASC";
        List<ParameterReading> list = new ArrayList<>();
        try (Connection conn = connectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, sampleId.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error finding readings by sampleId: " + sampleId, e);
        }
        return list;
    }

    @Override
    public Optional<ParameterReading> findBySampleAndParameter(UUID sampleId, UUID parameterId) {
        String sql = "SELECT * FROM readings WHERE sample_id = ? AND parameter_id = ?";
        try (Connection conn = connectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, sampleId.toString());
            ps.setString(2, parameterId.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error finding reading by sample and parameter", e);
        }
        return Optional.empty();
    }

    @Override
    public void save(ParameterReading r) {
        String sql = """
            INSERT INTO readings (id, sample_id, parameter_id, value_decimal, unit, entered_by, entered_at, provenance_kind, original_row)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
        try (Connection conn = connectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, r.getId().toString());
            ps.setString(2, r.getSampleId().toString());
            ps.setString(3, r.getParameterId().toString());
            ps.setString(4, r.getValueDecimal().toPlainString());
            ps.setString(5, r.getUnit());
            ps.setString(6, r.getEnteredBy().toString());
            ps.setString(7, r.getEnteredAt().toString());
            ps.setString(8, r.getProvenanceKind().name());
            if (r.getOriginalRow() != null) ps.setInt(9, r.getOriginalRow()); else ps.setNull(9, java.sql.Types.INTEGER);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error saving reading for sample " + r.getSampleId(), e);
        }
    }

    @Override
    public void deleteBySampleId(UUID sampleId) {
        String sql = "DELETE FROM readings WHERE sample_id = ?";
        try (Connection conn = connectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, sampleId.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error deleting readings for sample " + sampleId, e);
        }
    }

    private ParameterReading mapRow(ResultSet rs) throws SQLException {
        Integer origRow = rs.getObject("original_row") != null ? rs.getInt("original_row") : null;
        return new ParameterReading(
                UUID.fromString(rs.getString("id")),
                UUID.fromString(rs.getString("sample_id")),
                UUID.fromString(rs.getString("parameter_id")),
                new BigDecimal(rs.getString("value_decimal")),
                rs.getString("unit"),
                UUID.fromString(rs.getString("entered_by")),
                Instant.parse(rs.getString("entered_at")),
                RecordOrigin.valueOf(rs.getString("provenance_kind")),
                origRow
        );
    }
}
