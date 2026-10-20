package com.aquasentinel.application.service;

import com.aquasentinel.application.dto.DashboardDto;
import com.aquasentinel.application.port.AlertRepository;
import com.aquasentinel.application.port.ParameterRepository;
import com.aquasentinel.application.port.SampleRepository;
import com.aquasentinel.application.port.SourceRepository;
import com.aquasentinel.domain.enums.EvaluationOutcome;
import com.aquasentinel.infrastructure.db.ConnectionFactory;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class DashboardService {
    private final ConnectionFactory connectionFactory;
    private final SourceRepository sourceRepository;
    private final SampleRepository sampleRepository;
    private final AlertRepository alertRepository;
    private final ParameterRepository parameterRepository;

    public DashboardService(ConnectionFactory connectionFactory,
                            SourceRepository sourceRepository,
                            SampleRepository sampleRepository,
                            AlertRepository alertRepository,
                            ParameterRepository parameterRepository) {
        this.connectionFactory = Objects.requireNonNull(connectionFactory, "connectionFactory");
        this.sourceRepository = Objects.requireNonNull(sourceRepository, "sourceRepository");
        this.sampleRepository = Objects.requireNonNull(sampleRepository, "sampleRepository");
        this.alertRepository = Objects.requireNonNull(alertRepository, "alertRepository");
        this.parameterRepository = Objects.requireNonNull(parameterRepository, "parameterRepository");
    }

    public DashboardDto.KpiSummary getKpiSummary() {
        long activeSources = sourceRepository.findActive().size();
        long samplesCount = sampleRepository.count();
        long unresolvedAlerts = alertRepository.countUnresolved();
        long violationsCount = countViolations();

        return new DashboardDto.KpiSummary(
                activeSources,
                samplesCount,
                violationsCount,
                unresolvedAlerts
        );
    }

    public DashboardDto.TrendSeries getPointSampleTrend(UUID sourceId, UUID parameterId, Instant start, Instant end) {
        String sql = """
            SELECT s.collected_at, r.value_decimal, p.code as param_code, p.canonical_unit, ws.name as source_name
            FROM readings r
            JOIN samples s ON r.sample_id = s.id
            JOIN inspections i ON s.inspection_id = i.id
            JOIN sources ws ON i.source_id = ws.id
            JOIN parameters p ON r.parameter_id = p.id
            WHERE i.source_id = ?
              AND r.parameter_id = ?
              AND s.supersedes_sample_id IS NULL
              AND s.collected_at >= ?
              AND s.collected_at <= ?
            ORDER BY s.collected_at ASC
        """;

        List<DashboardDto.TrendDataPoint> points = new ArrayList<>();
        String paramCode = "Unknown";
        String unit = "";
        String sourceName = "Unknown";

        try (Connection conn = connectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, sourceId.toString());
            ps.setString(2, parameterId.toString());
            ps.setString(3, start != null ? start.toString() : Instant.EPOCH.toString());
            ps.setString(4, end != null ? end.toString() : Instant.now().plusSeconds(86400).toString());

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Instant time = Instant.parse(rs.getString("collected_at"));
                    BigDecimal val = new BigDecimal(rs.getString("value_decimal"));
                    paramCode = rs.getString("param_code");
                    unit = rs.getString("canonical_unit");
                    sourceName = rs.getString("source_name");

                    points.add(new DashboardDto.TrendDataPoint(
                            time, val, time.toString().substring(0, 10), sourceName, paramCode, unit
                    ));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error fetching trend data for source " + sourceId + " param " + parameterId, e);
        }

        return new DashboardDto.TrendSeries(paramCode, unit, sourceName, null, null, points);
    }

    private long countViolations() {
        String sql = "SELECT count(*) FROM evaluations WHERE outcome = ?";
        try (Connection conn = connectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, EvaluationOutcome.EXCEEDS_PROFILE.name());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error counting violations", e);
        }
        return 0;
    }
}
