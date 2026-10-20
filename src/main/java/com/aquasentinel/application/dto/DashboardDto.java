package com.aquasentinel.application.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public class DashboardDto {

    public record KpiSummary(
            long activeSourcesCount,
            long samplesInPeriodCount,
            long profileViolationsCount,
            long unresolvedAlertsCount
    ) {}

    public record TrendDataPoint(
            Instant timestamp,
            BigDecimal value,
            String label,
            String sourceName,
            String parameterCode,
            String unit
    ) {}

    public record TrendSeries(
            String parameterCode,
            String unit,
            String sourceName,
            BigDecimal lowerLimit,
            BigDecimal upperLimit,
            List<TrendDataPoint> points
    ) {}
}
