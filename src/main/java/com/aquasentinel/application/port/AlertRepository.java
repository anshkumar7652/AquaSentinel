package com.aquasentinel.application.port;

import com.aquasentinel.domain.enums.AlertState;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AlertRepository {
    record StoredAlert(
            UUID id,
            UUID evaluationId,
            AlertState state,
            UUID acknowledgedBy,
            Instant acknowledgedAt,
            Instant resolvedAt,
            int version
    ) {}

    Optional<StoredAlert> findById(UUID id);
    Optional<StoredAlert> findByEvaluationId(UUID evaluationId);
    List<StoredAlert> findByState(AlertState state);
    long countUnresolved();
    void save(StoredAlert alert);
    void update(StoredAlert alert);
}
