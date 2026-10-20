package com.aquasentinel.application.dto;

import com.aquasentinel.domain.model.ParameterDefinition;

import java.math.BigDecimal;
import java.util.UUID;

public record ParameterDto(
        UUID id,
        String code,
        String name,
        String canonicalUnit,
        BigDecimal inputMinDecimal,
        BigDecimal inputMaxDecimal
) {
    public static ParameterDto from(ParameterDefinition p) {
        return new ParameterDto(
                p.getId(),
                p.getCode(),
                p.getName(),
                p.getCanonicalUnit(),
                p.getInputMinDecimal(),
                p.getInputMaxDecimal()
        );
    }
}
