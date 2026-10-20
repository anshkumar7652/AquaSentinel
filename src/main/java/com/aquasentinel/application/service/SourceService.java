package com.aquasentinel.application.service;

import com.aquasentinel.application.dto.SourceDto;
import com.aquasentinel.application.port.AuditRepository;
import com.aquasentinel.application.port.SourceRepository;
import com.aquasentinel.application.security.Session;
import com.aquasentinel.domain.enums.SourceType;
import com.aquasentinel.domain.exception.AuthorizationException;
import com.aquasentinel.domain.exception.ValidationException;
import com.aquasentinel.domain.model.WaterSource;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class SourceService {
    private final SourceRepository sourceRepository;
    private final AuditRepository auditRepository;

    public SourceService(SourceRepository sourceRepository, AuditRepository auditRepository) {
        this.sourceRepository = sourceRepository;
        this.auditRepository = auditRepository;
    }

    public SourceDto createSource(Session session, String code, String name, SourceType type,
                                  String intendedUse, String locationText, Double latitude, Double longitude) {
        if (!session.canManageSources()) {
            throw new AuthorizationException("Only administrators can create water sources.");
        }
        validateSourceInputs(code, name, type, intendedUse, locationText, latitude, longitude);

        if (sourceRepository.findByCode(code.trim().toUpperCase()).isPresent()) {
            throw new ValidationException("Source with code '" + code.trim().toUpperCase() + "' already exists.");
        }

        UUID sourceId = UUID.randomUUID();
        Instant now = Instant.now();
        WaterSource source = new WaterSource(
                sourceId,
                code.trim().toUpperCase(),
                name.trim(),
                type,
                intendedUse.trim(),
                locationText.trim(),
                latitude,
                longitude,
                true,
                now,
                1
        );

        sourceRepository.save(source);
        auditRepository.record(session.userId(), "SOURCE_CREATED", "SOURCE", sourceId.toString(), now, null,
                "{\"code\":\"" + source.getCode() + "\",\"name\":\"" + source.getName() + "\"}");

        return SourceDto.from(source);
    }

    public void archiveSource(Session session, UUID sourceId) {
        if (!session.canManageSources()) {
            throw new AuthorizationException("Only administrators can archive water sources.");
        }
        WaterSource source = sourceRepository.findById(sourceId)
                .orElseThrow(() -> new ValidationException("Water source not found: " + sourceId));

        if (!source.isActive()) {
            return; // Already archived
        }

        WaterSource archived = new WaterSource(
                source.getId(),
                source.getCode(),
                source.getName(),
                source.getSourceType(),
                source.getIntendedUse(),
                source.getLocationText(),
                source.getLatitude(),
                source.getLongitude(),
                false,
                source.getCreatedAt(),
                source.getVersion()
        );

        sourceRepository.update(archived);
        auditRepository.record(session.userId(), "SOURCE_ARCHIVED", "SOURCE", sourceId.toString(), Instant.now(),
                "{\"active\":true}", "{\"active\":false}");
    }

    public Optional<SourceDto> getSource(Session session, UUID sourceId) {
        return sourceRepository.findById(sourceId).map(SourceDto::from);
    }

    public List<SourceDto> listActiveSources(Session session) {
        return sourceRepository.findActive().stream().map(SourceDto::from).toList();
    }

    public List<SourceDto> listSources(Session session, int offset, int limit) {
        return sourceRepository.findAll(offset, limit).stream().map(SourceDto::from).toList();
    }

    public long countSources(Session session) {
        return sourceRepository.count();
    }

    private void validateSourceInputs(String code, String name, SourceType type, String intendedUse,
                                    String locationText, Double latitude, Double longitude) {
        if (code == null || code.trim().isEmpty()) {
            throw new ValidationException("Source code cannot be empty.");
        }
        if (name == null || name.trim().isEmpty()) {
            throw new ValidationException("Source name cannot be empty.");
        }
        if (type == null) {
            throw new ValidationException("Source type must be specified.");
        }
        if (intendedUse == null || intendedUse.trim().isEmpty()) {
            throw new ValidationException("Intended use must be specified.");
        }
        if (locationText == null || locationText.trim().isEmpty()) {
            throw new ValidationException("Location description must be specified.");
        }
        if (latitude != null && (latitude < -90.0 || latitude > 90.0)) {
            throw new ValidationException("Latitude must be between -90.0 and +90.0 degrees.");
        }
        if (longitude != null && (longitude < -180.0 || longitude > 180.0)) {
            throw new ValidationException("Longitude must be between -180.0 and +180.0 degrees.");
        }
    }
}
