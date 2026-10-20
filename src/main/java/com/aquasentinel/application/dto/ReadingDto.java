package com.aquasentinel.application.dto;

import com.aquasentinel.domain.enums.RecordOrigin;
import com.aquasentinel.domain.model.ParameterReading;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ReadingDto(
        UUID id,
        UUID sampleId,
        UUID parameterId,
        String parameterCode,
        BigDecimal valueDecimal,
        String unit,
        UUID enteredBy,
        Instant enteredAt,
        RecordOrigin provenanceKind
) {
    public static ReadingDto of(ParameterReading r, String parameterCode) {
        return new ReadingDto(
                r.getId(),
                r.getSampleId(),
                r.getParameterId(),
                parameterCode,
                r.getValueDecimal(),
                r.getUnit(),
                r.getEnteredBy(),
                r.getEnteredAt(),
                r.getProvenanceKind()
        );
    }
}
