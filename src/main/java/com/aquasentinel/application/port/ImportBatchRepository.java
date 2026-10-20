package com.aquasentinel.application.port;

import com.aquasentinel.domain.model.HistoricalObservation;
import com.aquasentinel.domain.model.ImportBatch;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ImportBatchRepository {
    Optional<ImportBatch> findById(UUID id);
    Optional<ImportBatch> findByFileSha256(String fileSha256);
    List<ImportBatch> findAll();
    void save(ImportBatch batch);
    void saveObservations(List<HistoricalObservation> observations);
}
