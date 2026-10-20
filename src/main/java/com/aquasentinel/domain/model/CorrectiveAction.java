package com.aquasentinel.domain.model;

import com.aquasentinel.domain.enums.ActionState;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public class CorrectiveAction {
    private final UUID id;
    private final UUID alertId;
    private final UUID assignedUserId;
    private final String description;
    private final LocalDate dueDate;
    private final ActionState state;
    private final String completionNotes;
    private final UUID followupEvaluationId;
    private final Instant closedAt;
    private final Instant createdAt;
    private final int version;

    public CorrectiveAction(UUID id, UUID alertId, UUID assignedUserId, String description,
                            LocalDate dueDate, ActionState state, String completionNotes,
                            UUID followupEvaluationId, Instant closedAt,
                            Instant createdAt, int version) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.alertId = Objects.requireNonNull(alertId, "alertId must not be null");
        this.assignedUserId = Objects.requireNonNull(assignedUserId, "assignedUserId must not be null");
        this.description = Objects.requireNonNull(description, "description must not be null");
        this.dueDate = Objects.requireNonNull(dueDate, "dueDate must not be null");
        this.state = Objects.requireNonNull(state, "state must not be null");
        this.completionNotes = completionNotes;
        this.followupEvaluationId = followupEvaluationId;
        this.closedAt = closedAt;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.version = version;
    }

    public UUID getId() { return id; }
    public UUID getAlertId() { return alertId; }
    public UUID getAssignedUserId() { return assignedUserId; }
    public String getDescription() { return description; }
    public LocalDate getDueDate() { return dueDate; }
    public ActionState getState() { return state; }
    public String getCompletionNotes() { return completionNotes; }
    public UUID getFollowupEvaluationId() { return followupEvaluationId; }
    public Instant getClosedAt() { return closedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public int getVersion() { return version; }
}
