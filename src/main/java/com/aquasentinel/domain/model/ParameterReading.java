package com.aquasentinel.domain.model;

import com.aquasentinel.domain.enums.RecordOrigin;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class ParameterReading {
    private final UUID id;
    private final UUID sampleId;
    private final UUID parameterId;
    private final BigDecimal valueDecimal;
    private final String unit;
    private final UUID enteredBy;
    private final Instant enteredAt;
    private final RecordOrigin provenanceKind;
    private final Integer originalRow;

    public ParameterReading(UUID id, UUID sampleId, UUID parameterId, BigDecimal valueDecimal,
                            String unit, UUID enteredBy, Instant enteredAt,
                            RecordOrigin provenanceKind, Integer originalRow) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.sampleId = Objects.requireNonNull(sampleId, "sampleId must not be null");
        this.parameterId = Objects.requireNonNull(parameterId, "parameterId must not be null");
        this.valueDecimal = Objects.requireNonNull(valueDecimal, "valueDecimal must not be null");
        this.unit = Objects.requireNonNull(unit, "unit must not be null");
        this.enteredBy = Objects.requireNonNull(enteredBy, "enteredBy must not be null");
        this.enteredAt = Objects.requireNonNull(enteredAt, "enteredAt must not be null");
        this.provenanceKind = Objects.requireNonNull(provenanceKind, "provenanceKind must not be null");
        this.originalRow = originalRow;
    }

    public UUID getId() { return id; }
    public UUID getSampleId() { return sampleId; }
    public UUID getParameterId() { return parameterId; }
    public BigDecimal getValueDecimal() { return valueDecimal; }
    public String getUnit() { return unit; }
    public UUID getEnteredBy() { return enteredBy; }
    public Instant getEnteredAt() { return enteredAt; }
    public RecordOrigin getProvenanceKind() { return provenanceKind; }
    public Integer getOriginalRow() { return originalRow; }
}
