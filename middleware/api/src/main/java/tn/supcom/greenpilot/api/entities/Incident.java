package tn.supcom.greenpilot.api.entities;

import jakarta.nosql.Column;
import jakarta.nosql.Entity;
import tn.supcom.greenpilot.core.entities.NosqlEntity;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Grower-facing disease alert; groups the events of one plant while open (F09). OPEN -> ACKNOWLEDGED -> CLOSED. */
@Entity(Incident.COLLECTION)
public class Incident extends NosqlEntity {
    public static final String COLLECTION = "incidents";

    @Column
    private String siteId;
    @Column
    private String plantId;
    @Column
    private String zoneId;
    @Column
    private String label;
    @Column
    private IncidentStatus status;
    @Column
    private Instant openedAt;
    @Column
    private Instant lastSeenAt;
    @Column
    private int occurrences;
    @Column
    private List<String> eventIds = new ArrayList<>();
    @Column
    private Double lastScore;
    @Column
    private String modelVersion;
    @Column
    private List<Review> reviews = new ArrayList<>();
    @Column
    private String acknowledgedBy;
    @Column
    private Instant acknowledgedAt;
    @Column
    private String closedBy;
    @Column
    private Instant closedAt;
    @Column
    private String closeNote;

    // ---- getters / setters (generated)
    public String getSiteId() { return siteId; }
    public void setSiteId(String siteId) { this.siteId = siteId; }
    public String getPlantId() { return plantId; }
    public void setPlantId(String plantId) { this.plantId = plantId; }
    public String getZoneId() { return zoneId; }
    public void setZoneId(String zoneId) { this.zoneId = zoneId; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public IncidentStatus getStatus() { return status; }
    public void setStatus(IncidentStatus status) { this.status = status; }
    public Instant getOpenedAt() { return openedAt; }
    public void setOpenedAt(Instant openedAt) { this.openedAt = openedAt; }
    public Instant getLastSeenAt() { return lastSeenAt; }
    public void setLastSeenAt(Instant lastSeenAt) { this.lastSeenAt = lastSeenAt; }
    public int getOccurrences() { return occurrences; }
    public void setOccurrences(int occurrences) { this.occurrences = occurrences; }
    public List<String> getEventIds() { return eventIds; }
    public void setEventIds(List<String> eventIds) { this.eventIds = eventIds; }
    public Double getLastScore() { return lastScore; }
    public void setLastScore(Double lastScore) { this.lastScore = lastScore; }
    public String getModelVersion() { return modelVersion; }
    public void setModelVersion(String modelVersion) { this.modelVersion = modelVersion; }
    public List<Review> getReviews() { return reviews; }
    public void setReviews(List<Review> reviews) { this.reviews = reviews; }
    public String getAcknowledgedBy() { return acknowledgedBy; }
    public void setAcknowledgedBy(String acknowledgedBy) { this.acknowledgedBy = acknowledgedBy; }
    public Instant getAcknowledgedAt() { return acknowledgedAt; }
    public void setAcknowledgedAt(Instant acknowledgedAt) { this.acknowledgedAt = acknowledgedAt; }
    public String getClosedBy() { return closedBy; }
    public void setClosedBy(String closedBy) { this.closedBy = closedBy; }
    public Instant getClosedAt() { return closedAt; }
    public void setClosedAt(Instant closedAt) { this.closedAt = closedAt; }
    public String getCloseNote() { return closeNote; }
    public void setCloseNote(String closeNote) { this.closeNote = closeNote; }
}
