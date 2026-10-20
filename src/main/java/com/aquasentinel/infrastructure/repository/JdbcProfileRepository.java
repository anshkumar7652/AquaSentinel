package com.aquasentinel.infrastructure.repository;

import com.aquasentinel.application.port.ProfileRepository;
import com.aquasentinel.domain.enums.ProfileStatus;
import com.aquasentinel.domain.enums.RecordOrigin;
import com.aquasentinel.domain.model.ProfileVersion;
import com.aquasentinel.domain.model.ThresholdRule;
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

public class JdbcProfileRepository implements ProfileRepository {
    private final ConnectionFactory connectionFactory;

    public JdbcProfileRepository(ConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    @Override
    public Optional<ProfileVersion> findById(UUID id) {
        String sql = "SELECT * FROM profile_versions WHERE id = ?";
        try (Connection conn = connectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapProfileRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error finding profile by id: " + id, e);
        }
        return Optional.empty();
    }

    @Override
    public Optional<ProfileVersion> findByKeyAndVersion(String key, int versionNumber) {
        String sql = "SELECT * FROM profile_versions WHERE profile_key = ? AND version_number = ?";
        try (Connection conn = connectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, key);
            ps.setInt(2, versionNumber);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapProfileRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error finding profile by key and version", e);
        }
        return Optional.empty();
    }

    @Override
    public List<ProfileVersion> findAll() {
        String sql = "SELECT * FROM profile_versions ORDER BY profile_key ASC, version_number DESC";
        List<ProfileVersion> list = new ArrayList<>();
        try (Connection conn = connectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(mapProfileRow(rs));
        } catch (SQLException e) {
            throw new RuntimeException("Error finding all profile versions", e);
        }
        return list;
    }

    @Override
    public List<ThresholdRule> findRulesByProfileVersionId(UUID profileVersionId) {
        String sql = "SELECT * FROM profile_rules WHERE profile_version_id = ?";
        List<ThresholdRule> list = new ArrayList<>();
        try (Connection conn = connectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, profileVersionId.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRuleRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error finding rules for profile " + profileVersionId, e);
        }
        return list;
    }

    @Override
    public void save(ProfileVersion p) {
        String sql = """
            INSERT INTO profile_versions (id, profile_key, version_number, name, status, intended_use,
                                          allowed_source_types_json, record_kind, provenance_kind,
                                          reference_text, reference_url, published_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
        try (Connection conn = connectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, p.getId().toString());
            ps.setString(2, p.getProfileKey());
            ps.setInt(3, p.getVersionNumber());
            ps.setString(4, p.getName());
            ps.setString(5, p.getStatus().name());
            ps.setString(6, p.getIntendedUse());
            ps.setString(7, p.getAllowedSourceTypesJson());
            ps.setString(8, p.getRecordKind());
            ps.setString(9, p.getProvenanceKind().name());
            ps.setString(10, p.getReferenceText());
            ps.setString(11, p.getReferenceUrl());
            ps.setString(12, p.getPublishedAt() != null ? p.getPublishedAt().toString() : null);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error saving profile version: " + p.getName(), e);
        }
    }

    @Override
    public void saveRule(ThresholdRule r) {
        String sql = """
            INSERT INTO profile_rules (id, profile_version_id, parameter_id, lower_decimal, upper_decimal,
                                       lower_inclusive, upper_inclusive, required, unit)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
        try (Connection conn = connectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, r.getId().toString());
            ps.setString(2, r.getProfileVersionId().toString());
            ps.setString(3, r.getParameterId().toString());
            ps.setString(4, r.getLowerDecimal() != null ? r.getLowerDecimal().toPlainString() : null);
            ps.setString(5, r.getUpperDecimal() != null ? r.getUpperDecimal().toPlainString() : null);
            ps.setInt(6, r.isLowerInclusive() ? 1 : 0);
            ps.setInt(7, r.isUpperInclusive() ? 1 : 0);
            ps.setInt(8, r.isRequired() ? 1 : 0);
            ps.setString(9, r.getUnit());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error saving threshold rule for param: " + r.getParameterId(), e);
        }
    }

    private ProfileVersion mapProfileRow(ResultSet rs) throws SQLException {
        String pubStr = rs.getString("published_at");
        return new ProfileVersion(
                UUID.fromString(rs.getString("id")),
                rs.getString("profile_key"),
                rs.getInt("version_number"),
                rs.getString("name"),
                ProfileStatus.valueOf(rs.getString("status")),
                rs.getString("intended_use"),
                rs.getString("allowed_source_types_json"),
                rs.getString("record_kind"),
                RecordOrigin.valueOf(rs.getString("provenance_kind")),
                rs.getString("reference_text"),
                rs.getString("reference_url"),
                pubStr != null ? Instant.parse(pubStr) : null
        );
    }

    private ThresholdRule mapRuleRow(ResultSet rs) throws SQLException {
        String lowerStr = rs.getString("lower_decimal");
        String upperStr = rs.getString("upper_decimal");
        return new ThresholdRule(
                UUID.fromString(rs.getString("id")),
                UUID.fromString(rs.getString("profile_version_id")),
                UUID.fromString(rs.getString("parameter_id")),
                lowerStr != null ? new BigDecimal(lowerStr) : null,
                upperStr != null ? new BigDecimal(upperStr) : null,
                rs.getInt("lower_inclusive") == 1,
                rs.getInt("upper_inclusive") == 1,
                rs.getInt("required") == 1,
                rs.getString("unit")
        );
    }
}
