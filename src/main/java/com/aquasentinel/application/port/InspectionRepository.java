package com.aquasentinel.application.port;

import com.aquasentinel.domain.model.Inspection;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InspectionRepository {
    Optional<Inspection> findById(UUID id);
    List<Inspection> findBySourceId(UUID sourceId);
    List<Inspection> findByAssignedUserId(UUID assignedUserId);
    List<Inspection> findAll(int offset, int limit);
    long count();
    void save(Inspection inspection);
    void update(Inspection inspection);
}
