package com.aquasentinel.domain.model;

import com.aquasentinel.domain.enums.RecordOrigin;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class ImportBatch {
    private final UUID id;
    private final String fileSha256;
    private final String filename;
    private final String publisher;
    private final String datasetTitle;
    private final String sourceUrl;
    private final String accessedOn;
    private final String licenseStatus;
    private final String transformations;
    private final RecordOrigin provenanceKind;
    private final UUID importedBy;
    private final Instant importedAt;
    private final int rowCount;

    public ImportBatch(UUID id, String fileSha256, String filename, String publisher,
                       String datasetTitle, String sourceUrl, String accessedOn,
                       String licenseStatus, String transformations,
                       RecordOrigin provenanceKind, UUID importedBy,
                       Instant importedAt, int rowCount) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.fileSha256 = Objects.requireNonNull(fileSha256, "fileSha256 must not be null");
        this.filename = Objects.requireNonNull(filename, "filename must not be null");
        this.publisher = Objects.requireNonNull(publisher, "publisher must not be null");
        this.datasetTitle = Objects.requireNonNull(datasetTitle, "datasetTitle must not be null");
        this.sourceUrl = Objects.requireNonNull(sourceUrl, "sourceUrl must not be null");
        this.accessedOn = Objects.requireNonNull(accessedOn, "accessedOn must not be null");
        this.licenseStatus = Objects.requireNonNull(licenseStatus, "licenseStatus must not be null");
        this.transformations = Objects.requireNonNull(transformations, "transformations must not be null");
        this.provenanceKind = Objects.requireNonNull(provenanceKind, "provenanceKind must not be null");
        this.importedBy = Objects.requireNonNull(importedBy, "importedBy must not be null");
        this.importedAt = Objects.requireNonNull(importedAt, "importedAt must not be null");
        this.rowCount = rowCount;
    }

    public UUID getId() { return id; }
    public String getFileSha256() { return fileSha256; }
    public String getFilename() { return filename; }
    public String getPublisher() { return publisher; }
    public String getDatasetTitle() { return datasetTitle; }
    public String getSourceUrl() { return sourceUrl; }
    public String getAccessedOn() { return accessedOn; }
    public String getLicenseStatus() { return licenseStatus; }
    public String getTransformations() { return transformations; }
    public RecordOrigin getProvenanceKind() { return provenanceKind; }
    public UUID getImportedBy() { return importedBy; }
    public Instant getImportedAt() { return importedAt; }
    public int getRowCount() { return rowCount; }
}
