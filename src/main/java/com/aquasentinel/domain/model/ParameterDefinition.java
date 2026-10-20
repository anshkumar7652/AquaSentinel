package com.aquasentinel.domain.model;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

public class ParameterDefinition {
    private final UUID id;
    private final String code;
    private final String name;
    private final String canonicalUnit;
    private final BigDecimal inputMinDecimal;
    private final BigDecimal inputMaxDecimal;

    public ParameterDefinition(UUID id, String code, String name, String canonicalUnit,
                               BigDecimal inputMinDecimal, BigDecimal inputMaxDecimal) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.code = Objects.requireNonNull(code, "code must not be null");
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.canonicalUnit = Objects.requireNonNull(canonicalUnit, "canonicalUnit must not be null");
        this.inputMinDecimal = inputMinDecimal;
        this.inputMaxDecimal = inputMaxDecimal;
    }

    public UUID getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getCanonicalUnit() { return canonicalUnit; }
    public BigDecimal getInputMinDecimal() { return inputMinDecimal; }
    public BigDecimal getInputMaxDecimal() { return inputMaxDecimal; }
}
