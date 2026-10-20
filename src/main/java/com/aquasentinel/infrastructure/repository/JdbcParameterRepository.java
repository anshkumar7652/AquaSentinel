package com.aquasentinel.infrastructure.repository;

import com.aquasentinel.application.port.ParameterRepository;
import com.aquasentinel.domain.model.ParameterDefinition;
import com.aquasentinel.infrastructure.db.ConnectionFactory;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class JdbcParameterRepository implements ParameterRepository {
    private final ConnectionFactory connectionFactory;

    public JdbcParameterRepository(ConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    @Override
    public Optional<ParameterDefinition> findById(UUID id) {
        String sql = "SELECT * FROM parameters WHERE id = ?";
        try (Connection conn = connectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error finding parameter by id: " + id, e);
        }
        return Optional.empty();
    }

    @Override
    public Optional<ParameterDefinition> findByCode(String code) {
        String sql = "SELECT * FROM parameters WHERE code = ?";
        try (Connection conn = connectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, code);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error finding parameter by code: " + code, e);
        }
        return Optional.empty();
    }

    @Override
    public List<ParameterDefinition> findAll() {
        String sql = "SELECT * FROM parameters ORDER BY code ASC";
        List<ParameterDefinition> list = new ArrayList<>();
        try (Connection conn = connectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error finding all parameters", e);
        }
        return list;
    }

    @Override
    public long count() {
        String sql = "SELECT count(*) FROM parameters";
        try (Connection conn = connectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) return rs.getLong(1);
        } catch (SQLException e) {
            throw new RuntimeException("Error counting parameters", e);
        }
        return 0;
    }

    @Override
    public void save(ParameterDefinition param) {
        String sql = "INSERT INTO parameters (id, code, name, canonical_unit, input_min_decimal, input_max_decimal) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = connectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, param.getId().toString());
            ps.setString(2, param.getCode());
            ps.setString(3, param.getName());
            ps.setString(4, param.getCanonicalUnit());
            ps.setString(5, param.getInputMinDecimal() != null ? param.getInputMinDecimal().toPlainString() : null);
            ps.setString(6, param.getInputMaxDecimal() != null ? param.getInputMaxDecimal().toPlainString() : null);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error saving parameter: " + param.getCode(), e);
        }
    }

    private ParameterDefinition mapRow(ResultSet rs) throws SQLException {
        String minStr = rs.getString("input_min_decimal");
        String maxStr = rs.getString("input_max_decimal");
        return new ParameterDefinition(
                UUID.fromString(rs.getString("id")),
                rs.getString("code"),
                rs.getString("name"),
                rs.getString("canonical_unit"),
                minStr != null ? new BigDecimal(minStr) : null,
                maxStr != null ? new BigDecimal(maxStr) : null
        );
    }
}
