package com.aquasentinel.domain.model;

import com.aquasentinel.domain.enums.InspectionStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public class Inspection {
    private final UUID id;
    private final UUID sourceId;
    private final UUID assignedUserId;
    private final LocalDate dueDate;
    private final InspectionStatus status;
    private final String noSampleReason;
    private final String notes;
    private final Instant createdAt;
    private final int version;

    public Inspection(UUID id, UUID sourceId, UUID assignedUserId, LocalDate dueDate,
                      InspectionStatus status, String noSampleReason, String notes,
                      Instant createdAt, int version) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.sourceId = Objects.requireNonNull(sourceId, "sourceId must not be null");
        this.assignedUserId = Objects.requireNonNull(assignedUserId, "assignedUserId must not be null");
        this.dueDate = Objects.requireNonNull(dueDate, "dueDate must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.noSampleReason = noSampleReason;
        this.notes = notes != null ? notes : "";
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.version = version;
    }

    public UUID getId() { return id; }
    public UUID getSourceId() { return sourceId; }
    public UUID getAssignedUserId() { return assignedUserId; }
    public LocalDate getDueDate() { return dueDate; }
    public InspectionStatus getStatus() { return status; }
    public String getNoSampleReason() { return noSampleReason; }
    public String getNotes() { return notes; }
    public Instant getCreatedAt() { return createdAt; }
    public int getVersion() { return version; }
}
