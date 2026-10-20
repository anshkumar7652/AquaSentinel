package com.aquasentinel.application.port;

import com.aquasentinel.domain.model.WaterSample;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SampleRepository {
    Optional<WaterSample> findById(UUID id);
    Optional<WaterSample> findByCode(String code);
    List<WaterSample> findByInspectionId(UUID inspectionId);
    List<WaterSample> findAll(int offset, int limit);
    long count();
    void save(WaterSample sample);
    void update(WaterSample sample);
}
