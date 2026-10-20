package com.aquasentinel.application.port;

import com.aquasentinel.domain.model.ParameterReading;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReadingRepository {
    List<ParameterReading> findBySampleId(UUID sampleId);
    Optional<ParameterReading> findBySampleAndParameter(UUID sampleId, UUID parameterId);
    void save(ParameterReading reading);
    void deleteBySampleId(UUID sampleId);
}
