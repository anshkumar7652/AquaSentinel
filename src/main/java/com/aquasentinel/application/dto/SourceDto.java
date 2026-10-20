package com.aquasentinel.application.dto;

import com.aquasentinel.domain.enums.SourceType;
import com.aquasentinel.domain.model.WaterSource;

import java.time.Instant;
import java.util.UUID;

public record SourceDto(
        UUID id,
        String code,
        String name,
        SourceType sourceType,
        String intendedUse,
        String locationText,
        Double latitude,
        Double longitude,
        boolean active,
        Instant createdAt
) {
    public static SourceDto from(WaterSource s) {
        return new SourceDto(
                s.getId(),
                s.getCode(),
                s.getName(),
                s.getSourceType(),
                s.getIntendedUse(),
                s.getLocationText(),
                s.getLatitude(),
                s.getLongitude(),
                s.isActive(),
                s.getCreatedAt()
        );
    }
}
