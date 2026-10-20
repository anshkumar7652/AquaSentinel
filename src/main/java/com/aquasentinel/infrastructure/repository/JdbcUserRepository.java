package com.aquasentinel.infrastructure.repository;

import com.aquasentinel.application.port.UserRepository;
import com.aquasentinel.domain.enums.Role;
import com.aquasentinel.domain.model.UserAccount;
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

public class JdbcUserRepository implements UserRepository {
    private final ConnectionFactory connectionFactory;

    public JdbcUserRepository(ConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    @Override
    public Optional<UserAccount> findById(UUID id) {
        String sql = "SELECT * FROM users WHERE id = ?";
        try (Connection conn = connectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error finding user by id: " + id, e);
        }
        return Optional.empty();
    }

    @Override
    public Optional<UserAccount> findByUsername(String username) {
        String sql = "SELECT * FROM users WHERE username = ? COLLATE NOCASE";
        try (Connection conn = connectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error finding user by username: " + username, e);
        }
        return Optional.empty();
    }

    @Override
    public List<UserAccount> findAll() {
        String sql = "SELECT * FROM users ORDER BY username ASC";
        List<UserAccount> list = new ArrayList<>();
        try (Connection conn = connectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error finding all users", e);
        }
        return list;
    }

    @Override
    public long count() {
        String sql = "SELECT count(*) FROM users";
        try (Connection conn = connectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error counting users", e);
        }
        return 0;
    }

    @Override
    public void save(UserAccount user) {
        String sql = """
            INSERT INTO users (id, username, password_hash, salt, kdf_algorithm, kdf_iterations, role, active, created_at, version)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
        try (Connection conn = connectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, user.getId().toString());
            ps.setString(2, user.getUsername());
            ps.setString(3, user.getPasswordHash());
            ps.setString(4, user.getSalt());
            ps.setString(5, user.getKdfAlgorithm());
            ps.setInt(6, user.getKdfIterations());
            ps.setString(7, user.getRole().name());
            ps.setInt(8, user.isActive() ? 1 : 0);
            ps.setString(9, user.getCreatedAt().toString());
            ps.setInt(10, user.getVersion());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error saving user: " + user.getUsername(), e);
        }
    }

    @Override
    public void update(UserAccount user) {
        String sql = """
            UPDATE users SET active = ?, role = ?, version = version + 1
            WHERE id = ? AND version = ?
        """;
        try (Connection conn = connectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, user.isActive() ? 1 : 0);
            ps.setString(2, user.getRole().name());
            ps.setString(3, user.getId().toString());
            ps.setInt(4, user.getVersion());
            int affected = ps.executeUpdate();
            if (affected == 0) {
                throw new IllegalStateException("Optimistic locking failure updating user " + user.getId());
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error updating user: " + user.getId(), e);
        }
    }

    private UserAccount mapRow(ResultSet rs) throws SQLException {
        return new UserAccount(
                UUID.fromString(rs.getString("id")),
                rs.getString("username"),
                rs.getString("password_hash"),
                rs.getString("salt"),
                rs.getString("kdf_algorithm"),
                rs.getInt("kdf_iterations"),
                Role.valueOf(rs.getString("role")),
                rs.getInt("active") == 1,
                Instant.parse(rs.getString("created_at")),
                rs.getInt("version")
        );
    }
}
