package com.aquasentinel.application.dto;

import com.aquasentinel.domain.enums.RecordOrigin;
import com.aquasentinel.domain.enums.SampleStatus;
import com.aquasentinel.domain.model.WaterSample;

import java.time.Instant;
import java.util.UUID;

public record SampleDto(
        UUID id,
        String code,
        UUID inspectionId,
        SampleStatus status,
        Instant collectedAt,
        Instant receivedAt,
        UUID collectedBy,
        RecordOrigin origin,
        UUID supersedesSampleId,
        Instant createdAt
) {
    public static SampleDto from(WaterSample s) {
        return new SampleDto(
                s.getId(),
                s.getCode(),
                s.getInspectionId(),
                s.getStatus(),
                s.getCollectedAt(),
                s.getReceivedAt(),
                s.getCollectedBy(),
                s.getOrigin(),
                s.getSupersedesSampleId(),
                s.getCreatedAt()
        );
    }
}
