package com.aquasentinel.application.dto;

import com.aquasentinel.domain.enums.AlertState;

import java.time.Instant;
import java.util.UUID;

public record AlertDto(
        UUID id,
        UUID evaluationId,
        UUID sampleId,
        String sampleCode,
        String sourceName,
        AlertState state,
        UUID acknowledgedBy,
        String acknowledgedByName,
        Instant acknowledgedAt,
        Instant resolvedAt,
        int openActionCount,
        int totalActionCount,
        int version
) {}
