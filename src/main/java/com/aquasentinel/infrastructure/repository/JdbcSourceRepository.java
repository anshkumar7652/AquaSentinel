package com.aquasentinel.infrastructure.repository;

import com.aquasentinel.application.port.SourceRepository;
import com.aquasentinel.domain.enums.SourceType;
import com.aquasentinel.domain.model.WaterSource;
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

public class JdbcSourceRepository implements SourceRepository {
    private final ConnectionFactory connectionFactory;

    public JdbcSourceRepository(ConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    @Override
    public Optional<WaterSource> findById(UUID id) {
        String sql = "SELECT * FROM sources WHERE id = ?";
        try (Connection conn = connectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error finding source by id: " + id, e);
        }
        return Optional.empty();
    }

    @Override
    public Optional<WaterSource> findByCode(String code) {
        String sql = "SELECT * FROM sources WHERE code = ?";
        try (Connection conn = connectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, code);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error finding source by code: " + code, e);
        }
        return Optional.empty();
    }

    @Override
    public List<WaterSource> findAll(int offset, int limit) {
        String sql = "SELECT * FROM sources ORDER BY code ASC LIMIT ? OFFSET ?";
        List<WaterSource> list = new ArrayList<>();
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
            throw new RuntimeException("Error finding sources paginated", e);
        }
        return list;
    }

    @Override
    public List<WaterSource> findActive() {
        String sql = "SELECT * FROM sources WHERE active = 1 ORDER BY code ASC";
        List<WaterSource> list = new ArrayList<>();
        try (Connection conn = connectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error finding active sources", e);
        }
        return list;
    }

    @Override
    public long count() {
        String sql = "SELECT count(*) FROM sources";
        try (Connection conn = connectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error counting sources", e);
        }
        return 0;
    }

    @Override
    public void save(WaterSource source) {
        String sql = """
            INSERT INTO sources (id, code, name, source_type, intended_use, location_text, latitude, longitude, active, created_at, version)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
        try (Connection conn = connectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, source.getId().toString());
            ps.setString(2, source.getCode());
            ps.setString(3, source.getName());
            ps.setString(4, source.getSourceType().name());
            ps.setString(5, source.getIntendedUse());
            ps.setString(6, source.getLocationText());
            if (source.getLatitude() != null) ps.setDouble(7, source.getLatitude()); else ps.setNull(7, java.sql.Types.REAL);
            if (source.getLongitude() != null) ps.setDouble(8, source.getLongitude()); else ps.setNull(8, java.sql.Types.REAL);
            ps.setInt(9, source.isActive() ? 1 : 0);
            ps.setString(10, source.getCreatedAt().toString());
            ps.setInt(11, source.getVersion());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error saving source: " + source.getCode(), e);
        }
    }

    @Override
    public void update(WaterSource source) {
        String sql = """
            UPDATE sources SET name = ?, source_type = ?, intended_use = ?, location_text = ?,
                               latitude = ?, longitude = ?, active = ?, version = version + 1
            WHERE id = ? AND version = ?
        """;
        try (Connection conn = connectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, source.getName());
            ps.setString(2, source.getSourceType().name());
            ps.setString(3, source.getIntendedUse());
            ps.setString(4, source.getLocationText());
            if (source.getLatitude() != null) ps.setDouble(5, source.getLatitude()); else ps.setNull(5, java.sql.Types.REAL);
            if (source.getLongitude() != null) ps.setDouble(6, source.getLongitude()); else ps.setNull(6, java.sql.Types.REAL);
            ps.setInt(7, source.isActive() ? 1 : 0);
            ps.setString(8, source.getId().toString());
            ps.setInt(9, source.getVersion());
            int affected = ps.executeUpdate();
            if (affected == 0) {
                throw new IllegalStateException("Optimistic locking failure updating source " + source.getId());
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error updating source: " + source.getId(), e);
        }
    }

    private WaterSource mapRow(ResultSet rs) throws SQLException {
        Double lat = rs.getObject("latitude") != null ? rs.getDouble("latitude") : null;
        Double lng = rs.getObject("longitude") != null ? rs.getDouble("longitude") : null;
        return new WaterSource(
                UUID.fromString(rs.getString("id")),
                rs.getString("code"),
                rs.getString("name"),
                SourceType.valueOf(rs.getString("source_type")),
                rs.getString("intended_use"),
                rs.getString("location_text"),
                lat,
                lng,
                rs.getInt("active") == 1,
                Instant.parse(rs.getString("created_at")),
                rs.getInt("version")
        );
    }
}
