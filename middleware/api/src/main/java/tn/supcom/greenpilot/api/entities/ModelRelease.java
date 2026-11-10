package tn.supcom.greenpilot.api.entities;

import jakarta.nosql.Column;
import jakarta.nosql.Entity;
import tn.supcom.greenpilot.core.entities.NosqlEntity;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Versioned model release (MLOps 4.4): artifact location, checksum, metrics, dataset reference. */
@Entity(ModelRelease.COLLECTION)
public class ModelRelease extends NosqlEntity {
    public static final String COLLECTION = "model_releases";

    @Column
    private ModelKind kind;
    @Column
    private String modelVersion;   // e.g. 1.2.0 ("version" is the optimistic-lock counter)
    @Column
    private String artifactUri;
    @Column
    private String sha256;
    @Column
    private Map<String, Double> metrics = new HashMap<>();
    @Column
    private String datasetRef;
    @Column
    private String notes;
    @Column
    private String createdBy;
    @Column
    private Instant createdAt;
    @Column
    private List<Deployment> deployments = new ArrayList<>();

    // ---- getters / setters (generated)
    public ModelKind getKind() { return kind; }
    public void setKind(ModelKind kind) { this.kind = kind; }
    public String getModelVersion() { return modelVersion; }
    public void setModelVersion(String modelVersion) { this.modelVersion = modelVersion; }
    public String getArtifactUri() { return artifactUri; }
    public void setArtifactUri(String artifactUri) { this.artifactUri = artifactUri; }
    public String getSha256() { return sha256; }
    public void setSha256(String sha256) { this.sha256 = sha256; }
    public Map<String, Double> getMetrics() { return metrics; }
    public void setMetrics(Map<String, Double> metrics) { this.metrics = metrics; }
    public String getDatasetRef() { return datasetRef; }
    public void setDatasetRef(String datasetRef) { this.datasetRef = datasetRef; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public List<Deployment> getDeployments() { return deployments; }
    public void setDeployments(List<Deployment> deployments) { this.deployments = deployments; }
}
