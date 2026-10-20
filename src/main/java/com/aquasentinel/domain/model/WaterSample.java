package com.aquasentinel.domain.model;

import com.aquasentinel.domain.enums.RecordOrigin;
import com.aquasentinel.domain.enums.SampleStatus;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class WaterSample {
    private final UUID id;
    private final String code;
    private final UUID inspectionId;
    private final SampleStatus status;
    private final Instant collectedAt;
    private final Instant receivedAt;
    private final UUID collectedBy;
    private final RecordOrigin origin;
    private final UUID importBatchId;
    private final UUID supersedesSampleId;
    private final Instant createdAt;
    private final int version;

    public WaterSample(UUID id, String code, UUID inspectionId, SampleStatus status,
                       Instant collectedAt, Instant receivedAt, UUID collectedBy,
                       RecordOrigin origin, UUID importBatchId, UUID supersedesSampleId,
                       Instant createdAt, int version) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.code = Objects.requireNonNull(code, "code must not be null");
        this.inspectionId = Objects.requireNonNull(inspectionId, "inspectionId must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.collectedAt = collectedAt;
        this.receivedAt = receivedAt;
        this.collectedBy = collectedBy;
        this.origin = Objects.requireNonNull(origin, "origin must not be null");
        this.importBatchId = importBatchId;
        this.supersedesSampleId = supersedesSampleId;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.version = version;
    }

    public UUID getId() { return id; }
    public String getCode() { return code; }
    public UUID getInspectionId() { return inspectionId; }
    public SampleStatus getStatus() { return status; }
    public Instant getCollectedAt() { return collectedAt; }
    public Instant getReceivedAt() { return receivedAt; }
    public UUID getCollectedBy() { return collectedBy; }
    public RecordOrigin getOrigin() { return origin; }
    public UUID getImportBatchId() { return importBatchId; }
    public UUID getSupersedesSampleId() { return supersedesSampleId; }
    public Instant getCreatedAt() { return createdAt; }
    public int getVersion() { return version; }
}
