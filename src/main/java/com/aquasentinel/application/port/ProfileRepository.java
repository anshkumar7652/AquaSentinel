package com.aquasentinel.application.port;

import com.aquasentinel.domain.model.ProfileVersion;
import com.aquasentinel.domain.model.ThresholdRule;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProfileRepository {
    Optional<ProfileVersion> findById(UUID id);
    Optional<ProfileVersion> findByKeyAndVersion(String key, int versionNumber);
    List<ProfileVersion> findAll();
    List<ThresholdRule> findRulesByProfileVersionId(UUID profileVersionId);
    void save(ProfileVersion version);
    void saveRule(ThresholdRule rule);
}
