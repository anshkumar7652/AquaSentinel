package com.aquasentinel.application.port;

import com.aquasentinel.domain.enums.ActionState;
import com.aquasentinel.domain.model.CorrectiveAction;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ActionRepository {
    Optional<CorrectiveAction> findById(UUID id);
    List<CorrectiveAction> findByAlertId(UUID alertId);
    List<CorrectiveAction> findByAssignedUserId(UUID assignedUserId);
    List<CorrectiveAction> findByState(ActionState state);
    List<CorrectiveAction> findAll(int offset, int limit);
    long count();
    void save(CorrectiveAction action);
    void update(CorrectiveAction action);
}
