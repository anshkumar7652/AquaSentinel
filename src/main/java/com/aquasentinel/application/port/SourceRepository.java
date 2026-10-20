package com.aquasentinel.application.port;

import com.aquasentinel.domain.model.WaterSource;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SourceRepository {
    Optional<WaterSource> findById(UUID id);
    Optional<WaterSource> findByCode(String code);
    List<WaterSource> findAll(int offset, int limit);
    List<WaterSource> findActive();
    long count();
    void save(WaterSource source);
    void update(WaterSource source);
}
