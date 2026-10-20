package com.aquasentinel.application.port;

import java.time.Instant;
import java.util.UUID;

public interface AuditRepository {
    void record(UUID actorId, String action, String entityType, String entityId,
                Instant occurredAt, String beforeJson, String afterJson);
}
