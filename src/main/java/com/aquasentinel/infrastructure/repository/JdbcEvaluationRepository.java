package com.aquasentinel.infrastructure.repository;

import com.aquasentinel.application.port.EvaluationRepository;
import com.aquasentinel.domain.enums.EvaluationOutcome;
import com.aquasentinel.domain.evaluation.RuleEvaluationResult;
import com.aquasentinel.infrastructure.db.ConnectionFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class JdbcEvaluationRepository implements EvaluationRepository {
    private final ConnectionFactory connectionFactory;

    public JdbcEvaluationRepository(ConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    @Override
    public Optional<StoredEvaluation> findBySampleAndProfile(UUID sampleId, UUID profileVersionId) {
        String sql = "SELECT * FROM evaluations WHERE sample_id = ? AND profile_version_id = ?";
        try (Connection conn = connectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, sampleId.toString());
            ps.setString(2, profileVersionId.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error finding evaluation by sample and profile", e);
        }
        return Optional.empty();
    }

    @Override
    public Optional<StoredEvaluation> findById(UUID id) {
        String sql = "SELECT * FROM evaluations WHERE id = ?";
        try (Connection conn = connectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error finding evaluation by id", e);
        }
        return Optional.empty();
    }

    @Override
    public List<StoredEvaluation> findAll() {
        String sql = "SELECT * FROM evaluations ORDER BY evaluated_at DESC";
        List<StoredEvaluation> list = new java.util.ArrayList<>();
        try (Connection conn = connectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error listing all evaluations", e);
        }
        return list;
    }

    @Override
    public List<RuleEvaluationResult> findRuleResultsByEvaluationId(UUID evaluationId) {
        String sql = """
            SELECT rr.*, p.code as param_code
            FROM rule_results rr
            JOIN parameters p ON rr.parameter_id = p.id
            WHERE rr.evaluation_id = ?
        """;
        List<RuleEvaluationResult> list = new java.util.ArrayList<>();
        try (Connection conn = connectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, evaluationId.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String obsDec = rs.getString("observed_decimal");
                    String lowDec = rs.getString("lower_snapshot");
                    String upDec = rs.getString("upper_snapshot");

                    list.add(new RuleEvaluationResult(
                            UUID.fromString(rs.getString("parameter_id")),
                            rs.getString("param_code"),
                            com.aquasentinel.domain.enums.RuleResultOutcome.valueOf(rs.getString("result")),
                            obsDec != null ? new java.math.BigDecimal(obsDec) : null,
                            rs.getString("observed_unit"),
                            lowDec != null ? new java.math.BigDecimal(lowDec) : null,
                            upDec != null ? new java.math.BigDecimal(upDec) : null,
                            rs.getInt("lower_inclusive") == 1,
                            rs.getInt("upper_inclusive") == 1,
                            rs.getInt("required") == 1,
                            rs.getString("explanation")
                    ));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error loading rule results for evaluation " + evaluationId, e);
        }
        return list;
    }

    @Override
    public void save(StoredEvaluation eval, List<RuleEvaluationResult> ruleResults) {
        String evalSql = """
            INSERT INTO evaluations (id, sample_id, profile_version_id, outcome, evaluated_by, evaluated_at, input_digest)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        """;
        String ruleSql = """
            INSERT INTO rule_results (id, evaluation_id, parameter_id, result, observed_decimal, observed_unit,
                                      lower_snapshot, upper_snapshot, lower_inclusive, upper_inclusive, required, explanation)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;

        try (Connection conn = connectionFactory.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // 1. Insert evaluation record
                try (PreparedStatement ps = conn.prepareStatement(evalSql)) {
                    ps.setString(1, eval.id().toString());
                    ps.setString(2, eval.sampleId().toString());
                    ps.setString(3, eval.profileVersionId().toString());
                    ps.setString(4, eval.outcome().name());
                    ps.setString(5, eval.evaluatedBy().toString());
                    ps.setString(6, eval.evaluatedAt().toString());
                    ps.setString(7, eval.inputDigest());
                    ps.executeUpdate();
                }

                // 2. Insert rule snapshots
                try (PreparedStatement ps = conn.prepareStatement(ruleSql)) {
                    for (RuleEvaluationResult r : ruleResults) {
                        ps.setString(1, UUID.randomUUID().toString());
                        ps.setString(2, eval.id().toString());
                        ps.setString(3, r.parameterId().toString());
                        ps.setString(4, r.outcome().name());
                        ps.setString(5, r.observedValue() != null ? r.observedValue().toPlainString() : null);
                        ps.setString(6, r.observedUnit());
                        ps.setString(7, r.lowerLimit() != null ? r.lowerLimit().toPlainString() : null);
                        ps.setString(8, r.upperLimit() != null ? r.upperLimit().toPlainString() : null);
                        ps.setInt(9, r.lowerInclusive() ? 1 : 0);
                        ps.setInt(10, r.upperInclusive() ? 1 : 0);
                        ps.setInt(11, r.required() ? 1 : 0);
                        ps.setString(12, r.explanation());
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }

                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw new RuntimeException("Failed to persist evaluation atomically", e);
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Database error saving evaluation", e);
        }
    }

    private StoredEvaluation mapRow(ResultSet rs) throws SQLException {
        return new StoredEvaluation(
                UUID.fromString(rs.getString("id")),
                UUID.fromString(rs.getString("sample_id")),
                UUID.fromString(rs.getString("profile_version_id")),
                EvaluationOutcome.valueOf(rs.getString("outcome")),
                UUID.fromString(rs.getString("evaluated_by")),
                Instant.parse(rs.getString("evaluated_at")),
                rs.getString("input_digest")
        );
    }
}
