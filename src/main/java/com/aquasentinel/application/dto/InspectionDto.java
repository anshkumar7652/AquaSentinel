package com.aquasentinel.application.dto;

import com.aquasentinel.domain.enums.InspectionStatus;
import com.aquasentinel.domain.model.Inspection;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record InspectionDto(
        UUID id,
        UUID sourceId,
        UUID assignedUserId,
        LocalDate dueDate,
        InspectionStatus status,
        String noSampleReason,
        String notes,
        Instant createdAt
) {
    public static InspectionDto from(Inspection i) {
        return new InspectionDto(
                i.getId(),
                i.getSourceId(),
                i.getAssignedUserId(),
                i.getDueDate(),
                i.getStatus(),
                i.getNoSampleReason(),
                i.getNotes(),
                i.getCreatedAt()
        );
    }
}
