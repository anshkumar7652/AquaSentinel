package com.aquasentinel.application.port;

import com.aquasentinel.domain.model.ParameterDefinition;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ParameterRepository {
    Optional<ParameterDefinition> findById(UUID id);
    Optional<ParameterDefinition> findByCode(String code);
    List<ParameterDefinition> findAll();
    long count();
    void save(ParameterDefinition param);
}
