package com.aquasentinel.domain.model;

import com.aquasentinel.domain.enums.ProfileStatus;
import com.aquasentinel.domain.enums.RecordOrigin;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class ProfileVersion {
    private final UUID id;
    private final String profileKey;
    private final int versionNumber;
    private final String name;
    private final ProfileStatus status;
    private final String intendedUse;
    private final String allowedSourceTypesJson;
    private final String recordKind;
    private final RecordOrigin provenanceKind;
    private final String referenceText;
    private final String referenceUrl;
    private final Instant publishedAt;

    public ProfileVersion(UUID id, String profileKey, int versionNumber, String name,
                          ProfileStatus status, String intendedUse, String allowedSourceTypesJson,
                          String recordKind, RecordOrigin provenanceKind,
                          String referenceText, String referenceUrl, Instant publishedAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.profileKey = Objects.requireNonNull(profileKey, "profileKey must not be null");
        this.versionNumber = versionNumber;
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.intendedUse = Objects.requireNonNull(intendedUse, "intendedUse must not be null");
        this.allowedSourceTypesJson = Objects.requireNonNull(allowedSourceTypesJson, "allowedSourceTypesJson must not be null");
        this.recordKind = Objects.requireNonNull(recordKind, "recordKind must not be null");
        this.provenanceKind = Objects.requireNonNull(provenanceKind, "provenanceKind must not be null");
        this.referenceText = Objects.requireNonNull(referenceText, "referenceText must not be null");
        this.referenceUrl = referenceUrl;
        this.publishedAt = publishedAt;
    }

    public UUID getId() { return id; }
    public String getProfileKey() { return profileKey; }
    public int getVersionNumber() { return versionNumber; }
    public String getName() { return name; }
    public ProfileStatus getStatus() { return status; }
    public String getIntendedUse() { return intendedUse; }
    public String getAllowedSourceTypesJson() { return allowedSourceTypesJson; }
    public String getRecordKind() { return recordKind; }
    public RecordOrigin getProvenanceKind() { return provenanceKind; }
    public String getReferenceText() { return referenceText; }
    public String getReferenceUrl() { return referenceUrl; }
    public Instant getPublishedAt() { return publishedAt; }
}
