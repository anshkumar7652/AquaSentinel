package com.aquasentinel.application.service;

import com.aquasentinel.application.dto.ActionDto;
import com.aquasentinel.application.dto.AlertDto;
import com.aquasentinel.application.port.ActionRepository;
import com.aquasentinel.application.port.AlertRepository;
import com.aquasentinel.application.port.AuditRepository;
import com.aquasentinel.application.port.EvaluationRepository;
import com.aquasentinel.application.port.InspectionRepository;
import com.aquasentinel.application.port.SampleRepository;
import com.aquasentinel.application.port.SourceRepository;
import com.aquasentinel.application.port.UserRepository;
import com.aquasentinel.application.security.Session;
import com.aquasentinel.domain.enums.ActionState;
import com.aquasentinel.domain.enums.AlertState;
import com.aquasentinel.domain.enums.EvaluationOutcome;
import com.aquasentinel.domain.enums.RuleResultOutcome;
import com.aquasentinel.domain.evaluation.RuleEvaluationResult;
import com.aquasentinel.domain.exception.AuthorizationException;
import com.aquasentinel.domain.exception.ValidationException;
import com.aquasentinel.domain.model.CorrectiveAction;
import com.aquasentinel.domain.model.Inspection;
import com.aquasentinel.domain.model.UserAccount;
import com.aquasentinel.domain.model.WaterSample;
import com.aquasentinel.domain.model.WaterSource;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

public class ActionService {
    private final ActionRepository actionRepository;
    private final AlertRepository alertRepository;
    private final EvaluationRepository evaluationRepository;
    private final SampleRepository sampleRepository;
    private final InspectionRepository inspectionRepository;
    private final SourceRepository sourceRepository;
    private final UserRepository userRepository;
    private final AuditRepository auditRepository;

    public ActionService(ActionRepository actionRepository,
                         AlertRepository alertRepository,
                         EvaluationRepository evaluationRepository,
                         SampleRepository sampleRepository,
                         InspectionRepository inspectionRepository,
                         SourceRepository sourceRepository,
                         UserRepository userRepository,
                         AuditRepository auditRepository) {
        this.actionRepository = Objects.requireNonNull(actionRepository, "actionRepository");
        this.alertRepository = Objects.requireNonNull(alertRepository, "alertRepository");
        this.evaluationRepository = Objects.requireNonNull(evaluationRepository, "evaluationRepository");
        this.sampleRepository = Objects.requireNonNull(sampleRepository, "sampleRepository");
        this.inspectionRepository = Objects.requireNonNull(inspectionRepository, "inspectionRepository");
        this.sourceRepository = Objects.requireNonNull(sourceRepository, "sourceRepository");
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository");
        this.auditRepository = Objects.requireNonNull(auditRepository, "auditRepository");
    }

    public ActionDto createAction(Session session, UUID alertId, UUID assignedUserId, String description, LocalDate dueDate) {
        if (!session.isAnalystOrAdmin()) {
            throw new AuthorizationException("Only analysts or administrators can create corrective actions.");
        }
        if (description == null || description.trim().isBlank()) {
            throw new ValidationException("Action description cannot be blank.");
        }
        if (dueDate == null) {
            throw new ValidationException("Due date is required.");
        }
        alertRepository.findById(alertId)
                .orElseThrow(() -> new ValidationException("Alert not found: " + alertId));
        UserAccount assignee = userRepository.findById(assignedUserId)
                .orElseThrow(() -> new ValidationException("Assigned user not found: " + assignedUserId));

        UUID actionId = UUID.randomUUID();
        Instant now = Instant.now();
        CorrectiveAction action = new CorrectiveAction(
                actionId, alertId, assignedUserId, description.trim(), dueDate,
                ActionState.OPEN, null, null, null, now, 1
        );
        actionRepository.save(action);

        auditRepository.record(session.userId(), "CORRECTIVE_ACTION_CREATED", "CORRECTIVE_ACTION", actionId.toString(),
                now, null, "{\"alertId\":\"" + alertId + "\",\"assignedTo\":\"" + assignee.getUsername() + "\"}");

        return toDto(action, assignee.getUsername());
    }

    public ActionDto updateState(Session session, UUID actionId, ActionState newState) {
        CorrectiveAction action = actionRepository.findById(actionId)
                .orElseThrow(() -> new ValidationException("Corrective action not found: " + actionId));

        if (!canModifyAction(session, action)) {
            throw new AuthorizationException("User not authorized to update this corrective action.");
        }

        if (newState == ActionState.CLOSED) {
            throw new ValidationException("Use closeAction() to verify requirements and close an action.");
        }

        CorrectiveAction updated = new CorrectiveAction(
                action.getId(), action.getAlertId(), action.getAssignedUserId(), action.getDescription(),
                action.getDueDate(), newState, action.getCompletionNotes(), action.getFollowupEvaluationId(),
                null, action.getCreatedAt(), action.getVersion()
        );
        actionRepository.update(updated);

        auditRepository.record(session.userId(), "CORRECTIVE_ACTION_STATE_UPDATED", "CORRECTIVE_ACTION",
                action.getId().toString(), Instant.now(),
                "{\"state\":\"" + action.getState() + "\"}",
                "{\"state\":\"" + newState + "\"}");

        UserAccount assignee = userRepository.findById(action.getAssignedUserId()).orElse(null);
        return toDto(updated, assignee != null ? assignee.getUsername() : "Unknown");
    }

    public ActionDto closeAction(Session session, UUID actionId, String completionNotes, UUID followupEvaluationId) {
        CorrectiveAction action = actionRepository.findById(actionId)
                .orElseThrow(() -> new ValidationException("Corrective action not found: " + actionId));

        if (!canModifyAction(session, action)) {
            throw new AuthorizationException("User not authorized to close this corrective action.");
        }

        if (action.getState() == ActionState.CLOSED) {
            UserAccount assignee = userRepository.findById(action.getAssignedUserId()).orElse(null);
            return toDto(action, assignee != null ? assignee.getUsername() : "Unknown");
        }

        if (completionNotes == null || completionNotes.trim().isBlank()) {
            throw new ValidationException("Completion notes are mandatory to close a corrective action.");
        }

        if (followupEvaluationId == null) {
            throw new ValidationException("A valid passing follow-up evaluation is required to close a corrective action.");
        }

        // 1. Fetch triggering alert & evaluation context
        AlertRepository.StoredAlert alert = alertRepository.findById(action.getAlertId())
                .orElseThrow(() -> new ValidationException("Triggering alert not found: " + action.getAlertId()));

        EvaluationRepository.StoredEvaluation originalEval = evaluationRepository.findById(alert.evaluationId())
                .orElseThrow(() -> new ValidationException("Triggering evaluation not found: " + alert.evaluationId()));

        WaterSample originalSample = sampleRepository.findById(originalEval.sampleId())
                .orElseThrow(() -> new ValidationException("Triggering sample not found: " + originalEval.sampleId()));

        Inspection originalInspection = inspectionRepository.findById(originalSample.getInspectionId())
                .orElseThrow(() -> new ValidationException("Triggering inspection not found: " + originalSample.getInspectionId()));

        // 2. Fetch and validate follow-up evaluation
        EvaluationRepository.StoredEvaluation followEval = evaluationRepository.findById(followupEvaluationId)
                .orElseThrow(() -> new ValidationException("Follow-up evaluation not found: " + followupEvaluationId));

        WaterSample followSample = sampleRepository.findById(followEval.sampleId())
                .orElseThrow(() -> new ValidationException("Follow-up sample not found: " + followEval.sampleId()));

        Inspection followInspection = inspectionRepository.findById(followSample.getInspectionId())
                .orElseThrow(() -> new ValidationException("Follow-up inspection not found: " + followSample.getInspectionId()));

        // Rule A: Same source
        if (!originalInspection.getSourceId().equals(followInspection.getSourceId())) {
            throw new ValidationException("Follow-up evaluation must be from the same water source as the triggering sample.");
        }

        // Rule B: Same profile version
        if (!originalEval.profileVersionId().equals(followEval.profileVersionId())) {
            throw new ValidationException("Follow-up evaluation must evaluate against the exact same profile version.");
        }

        // Rule C: Follow-up sample collection must be later than original collection
        if (!followSample.getCollectedAt().isAfter(originalSample.getCollectedAt())) {
            throw new ValidationException("Follow-up sample collection time must be strictly after the original sample collection time.");
        }

        // Rule D: Outcome must be WITHIN_PROFILE
        if (followEval.outcome() != EvaluationOutcome.WITHIN_PROFILE) {
            throw new ValidationException("Follow-up evaluation outcome must be WITHIN_PROFILE. Found: " + followEval.outcome());
        }

        // Rule E: No missing required parameters
        List<RuleEvaluationResult> ruleResults = evaluationRepository.findRuleResultsByEvaluationId(followupEvaluationId);
        boolean hasMissingRequired = ruleResults.stream().anyMatch(r -> r.required() && r.outcome() == RuleResultOutcome.MISSING);
        if (hasMissingRequired) {
            throw new ValidationException("Follow-up evaluation has missing required parameters; cannot verify compliance.");
        }

        Instant now = Instant.now();
        CorrectiveAction closed = new CorrectiveAction(
                action.getId(), action.getAlertId(), action.getAssignedUserId(), action.getDescription(),
                action.getDueDate(), ActionState.CLOSED, completionNotes.trim(), followupEvaluationId,
                now, action.getCreatedAt(), action.getVersion()
        );
        actionRepository.update(closed);

        auditRepository.record(session.userId(), "CORRECTIVE_ACTION_CLOSED", "CORRECTIVE_ACTION",
                closed.getId().toString(), now, "{\"state\":\"" + action.getState() + "\"}",
                "{\"state\":\"CLOSED\",\"followupEvaluationId\":\"" + followupEvaluationId + "\"}");

        // Check if all actions linked to this alert are now CLOSED
        List<CorrectiveAction> allActions = actionRepository.findByAlertId(alert.id());
        boolean allClosed = allActions.stream().allMatch(a -> a.getId().equals(closed.getId()) || a.getState() == ActionState.CLOSED);

        if (allClosed && alert.state() != AlertState.RESOLVED) {
            AlertRepository.StoredAlert resolvedAlert = new AlertRepository.StoredAlert(
                    alert.id(), alert.evaluationId(), AlertState.RESOLVED,
                    alert.acknowledgedBy(), alert.acknowledgedAt(), now, alert.version()
            );
            alertRepository.update(resolvedAlert);

            auditRepository.record(session.userId(), "ALERT_RESOLVED", "ALERT",
                    alert.id().toString(), now, "{\"state\":\"" + alert.state() + "\"}",
                    "{\"state\":\"RESOLVED\"}");
        }

        UserAccount assignee = userRepository.findById(action.getAssignedUserId()).orElse(null);
        return toDto(closed, assignee != null ? assignee.getUsername() : "Unknown");
    }

    public AlertDto acknowledgeAlert(Session session, UUID alertId) {
        if (!session.isAnalystOrAdmin()) {
            throw new AuthorizationException("Only analysts or administrators can acknowledge alerts.");
        }

        AlertRepository.StoredAlert alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new ValidationException("Alert not found: " + alertId));

        if (alert.state() == AlertState.RESOLVED) {
            throw new ValidationException("Cannot acknowledge an already resolved alert.");
        }

        Instant now = Instant.now();
        AlertRepository.StoredAlert ackAlert = new AlertRepository.StoredAlert(
                alert.id(), alert.evaluationId(), AlertState.ACKNOWLEDGED,
                session.userId(), now, alert.resolvedAt(), alert.version()
        );
        alertRepository.update(ackAlert);

        auditRepository.record(session.userId(), "ALERT_ACKNOWLEDGED", "ALERT",
                alert.id().toString(), now, "{\"state\":\"" + alert.state() + "\"}",
                "{\"state\":\"ACKNOWLEDGED\"}");

        return toAlertDto(ackAlert);
    }

    public List<AlertDto> getAlerts(AlertState filterState) {
        List<AlertRepository.StoredAlert> alerts;
        if (filterState == null) {
            alerts = new ArrayList<>();
            alerts.addAll(alertRepository.findByState(AlertState.OPEN));
            alerts.addAll(alertRepository.findByState(AlertState.ACKNOWLEDGED));
            alerts.addAll(alertRepository.findByState(AlertState.RESOLVED));
        } else {
            alerts = alertRepository.findByState(filterState);
        }
        return alerts.stream().map(this::toAlertDto).toList();
    }

    public List<ActionDto> getActionsByAlert(UUID alertId) {
        Map<UUID, String> userNames = userRepository.findAll().stream()
                .collect(Collectors.toMap(UserAccount::getId, UserAccount::getUsername));

        return actionRepository.findByAlertId(alertId).stream()
                .map(a -> toDto(a, userNames.getOrDefault(a.getAssignedUserId(), "Unknown")))
                .toList();
    }

    public List<ActionDto> getAllActions(int offset, int limit) {
        Map<UUID, String> userNames = userRepository.findAll().stream()
                .collect(Collectors.toMap(UserAccount::getId, UserAccount::getUsername));

        return actionRepository.findAll(offset, limit).stream()
                .map(a -> toDto(a, userNames.getOrDefault(a.getAssignedUserId(), "Unknown")))
                .toList();
    }

    private boolean canModifyAction(Session session, CorrectiveAction action) {
        if (session.isAnalystOrAdmin()) {
            return true;
        }
        return session.userId().equals(action.getAssignedUserId());
    }

    private ActionDto toDto(CorrectiveAction a, String username) {
        return new ActionDto(
                a.getId(), a.getAlertId(), a.getAssignedUserId(), username,
                a.getDescription(), a.getDueDate(), a.getState(), a.getCompletionNotes(),
                a.getFollowupEvaluationId(), a.getClosedAt(), a.getCreatedAt(), a.getVersion()
        );
    }

    private AlertDto toAlertDto(AlertRepository.StoredAlert alert) {
        String ackByName = null;
        if (alert.acknowledgedBy() != null) {
            ackByName = userRepository.findById(alert.acknowledgedBy())
                    .map(UserAccount::getUsername).orElse(null);
        }

        String sampleCode = "Unknown";
        String sourceName = "Unknown";
        UUID sampleId = null;

        var evalOpt = evaluationRepository.findById(alert.evaluationId());
        if (evalOpt.isPresent()) {
            sampleId = evalOpt.get().sampleId();
            var sampleOpt = sampleRepository.findById(sampleId);
            if (sampleOpt.isPresent()) {
                WaterSample sample = sampleOpt.get();
                sampleCode = sample.getCode();
                var inspOpt = inspectionRepository.findById(sample.getInspectionId());
                if (inspOpt.isPresent()) {
                    var srcOpt = sourceRepository.findById(inspOpt.get().getSourceId());
                    if (srcOpt.isPresent()) {
                        sourceName = srcOpt.get().getName();
                    }
                }
            }
        }

        List<CorrectiveAction> actions = actionRepository.findByAlertId(alert.id());
        int openCount = (int) actions.stream().filter(a -> a.getState() != ActionState.CLOSED).count();

        return new AlertDto(
                alert.id(), alert.evaluationId(), sampleId, sampleCode, sourceName,
                alert.state(), alert.acknowledgedBy(), ackByName, alert.acknowledgedAt(),
                alert.resolvedAt(), openCount, actions.size(), alert.version()
        );
    }
}
