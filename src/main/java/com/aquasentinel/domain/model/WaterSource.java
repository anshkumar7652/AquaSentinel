package com.aquasentinel.domain.model;

import com.aquasentinel.domain.enums.SourceType;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class WaterSource {
    private final UUID id;
    private final String code;
    private final String name;
    private final SourceType sourceType;
    private final String intendedUse;
    private final String locationText;
    private final Double latitude;
    private final Double longitude;
    private final boolean active;
    private final Instant createdAt;
    private final int version;

    public WaterSource(UUID id, String code, String name, SourceType sourceType,
                       String intendedUse, String locationText, Double latitude, Double longitude,
                       boolean active, Instant createdAt, int version) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.code = Objects.requireNonNull(code, "code must not be null");
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.sourceType = Objects.requireNonNull(sourceType, "sourceType must not be null");
        this.intendedUse = Objects.requireNonNull(intendedUse, "intendedUse must not be null");
        this.locationText = Objects.requireNonNull(locationText, "locationText must not be null");
        this.latitude = latitude;
        this.longitude = longitude;
        this.active = active;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.version = version;
    }

    public UUID getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public SourceType getSourceType() { return sourceType; }
    public String getIntendedUse() { return intendedUse; }
    public String getLocationText() { return locationText; }
    public Double getLatitude() { return latitude; }
    public Double getLongitude() { return longitude; }
    public boolean isActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }
    public int getVersion() { return version; }
}
