package com.aquasentinel.domain.model;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

public class ThresholdRule {
    private final UUID id;
    private final UUID profileVersionId;
    private final UUID parameterId;
    private final BigDecimal lowerDecimal;
    private final BigDecimal upperDecimal;
    private final boolean lowerInclusive;
    private final boolean upperInclusive;
    private final boolean required;
    private final String unit;

    public ThresholdRule(UUID id, UUID profileVersionId, UUID parameterId,
                         BigDecimal lowerDecimal, BigDecimal upperDecimal,
                         boolean lowerInclusive, boolean upperInclusive,
                         boolean required, String unit) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.profileVersionId = Objects.requireNonNull(profileVersionId, "profileVersionId must not be null");
        this.parameterId = Objects.requireNonNull(parameterId, "parameterId must not be null");
        this.lowerDecimal = lowerDecimal;
        this.upperDecimal = upperDecimal;
        this.lowerInclusive = lowerInclusive;
        this.upperInclusive = upperInclusive;
        this.required = required;
        this.unit = Objects.requireNonNull(unit, "unit must not be null");
    }

    public UUID getId() { return id; }
    public UUID getProfileVersionId() { return profileVersionId; }
    public UUID getParameterId() { return parameterId; }
    public BigDecimal getLowerDecimal() { return lowerDecimal; }
    public BigDecimal getUpperDecimal() { return upperDecimal; }
    public boolean isLowerInclusive() { return lowerInclusive; }
    public boolean isUpperInclusive() { return upperInclusive; }
    public boolean isRequired() { return required; }
    public String getUnit() { return unit; }
}
