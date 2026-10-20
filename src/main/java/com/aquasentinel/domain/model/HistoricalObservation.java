package com.aquasentinel.domain.model;

import com.aquasentinel.domain.enums.SummaryStatistic;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public class HistoricalObservation {
    private final UUID id;
    private final UUID sourceId;
    private final UUID parameterId;
    private final LocalDate periodStart;
    private final LocalDate periodEnd;
    private final SummaryStatistic statistic;
    private final BigDecimal valueDecimal;
    private final String unit;
    private final UUID importBatchId;
    private final int originalRow;

    public HistoricalObservation(UUID id, UUID sourceId, UUID parameterId,
                                 LocalDate periodStart, LocalDate periodEnd,
                                 SummaryStatistic statistic, BigDecimal valueDecimal,
                                 String unit, UUID importBatchId, int originalRow) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.sourceId = Objects.requireNonNull(sourceId, "sourceId must not be null");
        this.parameterId = Objects.requireNonNull(parameterId, "parameterId must not be null");
        this.periodStart = Objects.requireNonNull(periodStart, "periodStart must not be null");
        this.periodEnd = Objects.requireNonNull(periodEnd, "periodEnd must not be null");
        this.statistic = Objects.requireNonNull(statistic, "statistic must not be null");
        this.valueDecimal = Objects.requireNonNull(valueDecimal, "valueDecimal must not be null");
        this.unit = Objects.requireNonNull(unit, "unit must not be null");
        this.importBatchId = Objects.requireNonNull(importBatchId, "importBatchId must not be null");
        this.originalRow = originalRow;
    }

    public UUID getId() { return id; }
    public UUID getSourceId() { return sourceId; }
    public UUID getParameterId() { return parameterId; }
    public LocalDate getPeriodStart() { return periodStart; }
    public LocalDate getPeriodEnd() { return periodEnd; }
    public SummaryStatistic getStatistic() { return statistic; }
    public BigDecimal getValueDecimal() { return valueDecimal; }
    public String getUnit() { return unit; }
    public UUID getImportBatchId() { return importBatchId; }
    public int getOriginalRow() { return originalRow; }
}
