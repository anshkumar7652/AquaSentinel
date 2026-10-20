package com.aquasentinel.application.dto;

import com.aquasentinel.domain.enums.ActionState;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ActionDto(
        UUID id,
        UUID alertId,
        UUID assignedUserId,
        String assignedUsername,
        String description,
        LocalDate dueDate,
        ActionState state,
        String completionNotes,
        UUID followupEvaluationId,
        Instant closedAt,
        Instant createdAt,
        int version
) {}
