package com.aquasentinel.application.service;

import com.aquasentinel.application.dto.InspectionDto;
import com.aquasentinel.application.port.AuditRepository;
import com.aquasentinel.application.port.InspectionRepository;
import com.aquasentinel.application.port.SourceRepository;
import com.aquasentinel.application.port.UserRepository;
import com.aquasentinel.application.security.Session;
import com.aquasentinel.domain.enums.InspectionStatus;
import com.aquasentinel.domain.enums.Role;
import com.aquasentinel.domain.exception.AuthorizationException;
import com.aquasentinel.domain.exception.ValidationException;
import com.aquasentinel.domain.model.Inspection;
import com.aquasentinel.domain.model.UserAccount;
import com.aquasentinel.domain.model.WaterSource;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class InspectionService {
    private final InspectionRepository inspectionRepository;
    private final SourceRepository sourceRepository;
    private final UserRepository userRepository;
    private final AuditRepository auditRepository;

    public InspectionService(InspectionRepository inspectionRepository,
                             SourceRepository sourceRepository,
                             UserRepository userRepository,
                             AuditRepository auditRepository) {
        this.inspectionRepository = inspectionRepository;
        this.sourceRepository = sourceRepository;
        this.userRepository = userRepository;
        this.auditRepository = auditRepository;
    }

    public InspectionDto scheduleInspection(Session session, UUID sourceId, UUID assignedUserId,
                                            LocalDate dueDate, String notes) {
        if (!session.isAdministrator()) {
            throw new AuthorizationException("Only administrators can schedule inspections.");
        }
        if (dueDate == null) {
            throw new ValidationException("Due date cannot be null.");
        }

        WaterSource source = sourceRepository.findById(sourceId)
                .orElseThrow(() -> new ValidationException("Source not found: " + sourceId));
        if (!source.isActive()) {
            throw new ValidationException("Cannot schedule inspection for an archived source.");
        }

        UserAccount user = userRepository.findById(assignedUserId)
                .orElseThrow(() -> new ValidationException("Assigned user not found: " + assignedUserId));
        if (!user.isActive()) {
            throw new ValidationException("Cannot assign inspection to a disabled user.");
        }
        if (user.getRole() != Role.FIELD_OFFICER && user.getRole() != Role.ADMINISTRATOR) {
            throw new ValidationException("Only field officers or administrators can be assigned inspections.");
        }

        UUID inspectionId = UUID.randomUUID();
        Instant now = Instant.now();
        Inspection inspection = new Inspection(
                inspectionId,
                sourceId,
                assignedUserId,
                dueDate,
                InspectionStatus.SCHEDULED,
                null,
                notes != null ? notes.trim() : "",
                now,
                1
        );

        inspectionRepository.save(inspection);
        auditRepository.record(session.userId(), "INSPECTION_SCHEDULED", "INSPECTION", inspectionId.toString(), now, null,
                "{\"sourceId\":\"" + sourceId + "\",\"assignedUserId\":\"" + assignedUserId + "\"}");

        return InspectionDto.from(inspection);
    }

    public void updateStatus(Session session, UUID inspectionId, InspectionStatus newStatus, String noSampleReason) {
        Inspection inspection = inspectionRepository.findById(inspectionId)
                .orElseThrow(() -> new ValidationException("Inspection not found: " + inspectionId));

        // Check authorization: Admin or assigned officer
        boolean isAdmin = session.isAdministrator();
        boolean isAssigned = session.userId().equals(inspection.getAssignedUserId());
        if (!isAdmin && !isAssigned) {
            throw new AuthorizationException("You are not authorized to update this inspection.");
        }

        // Validate state transitions
        if (inspection.getStatus() == InspectionStatus.CANCELLED) {
            throw new ValidationException("Cannot change status of a cancelled inspection.");
        }
        if (inspection.getStatus() == InspectionStatus.COMPLETED) {
            throw new ValidationException("Cannot change status of an already completed inspection.");
        }

        if (newStatus == InspectionStatus.COMPLETED && (noSampleReason == null || noSampleReason.trim().isEmpty())) {
            // Note: If no sample is collected, reason is required per FR-02.
            // If sample was collected, sample tracking service checks will integrate here in M3.
        }

        Inspection updated = new Inspection(
                inspection.getId(),
                inspection.getSourceId(),
                inspection.getAssignedUserId(),
                inspection.getDueDate(),
                newStatus,
                noSampleReason != null ? noSampleReason.trim() : inspection.getNoSampleReason(),
                inspection.getNotes(),
                inspection.getCreatedAt(),
                inspection.getVersion()
        );

        inspectionRepository.update(updated);
        auditRepository.record(session.userId(), "INSPECTION_STATUS_CHANGED", "INSPECTION", inspectionId.toString(),
                Instant.now(), "{\"status\":\"" + inspection.getStatus() + "\"}", "{\"status\":\"" + newStatus + "\"}");
    }

    public List<InspectionDto> listInspections(Session session, int offset, int limit) {
        return inspectionRepository.findAll(offset, limit).stream().map(InspectionDto::from).toList();
    }

    public List<InspectionDto> listBySource(Session session, UUID sourceId) {
        return inspectionRepository.findBySourceId(sourceId).stream().map(InspectionDto::from).toList();
    }

    public Optional<InspectionDto> getInspection(Session session, UUID id) {
        return inspectionRepository.findById(id).map(InspectionDto::from);
    }
}
