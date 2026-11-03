package tn.supcom.greenpilot.api.entities;

import jakarta.nosql.Column;
import jakarta.nosql.Entity;
import tn.supcom.greenpilot.core.entities.NosqlEntity;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Confirmed disease event sent by the Pi (3 consecutive positives). id = eventId. */
@Entity(DiseaseEvent.COLLECTION)
public class DiseaseEvent extends NosqlEntity {
    public static final String COLLECTION = "disease_events";

    @Column
    private String siteId;
    @Column
    private String gatewayId;
    @Column
    private String plantId;
    @Column
    private String zoneId;
    @Column
    private Instant ts;
    @Column
    private String label;
    @Column
    private Double score;
    @Column
    private String modelVersion;
    @Column
    private String imageKey;
    @Column
    private Integer imageW;
    @Column
    private Integer imageH;
    @Column
    private List<Detection> detections = new ArrayList<>();
    @Column
    private boolean acked;
    @Column
    private Instant receivedAt;

    // ---- getters / setters (generated)
    public String getSiteId() { return siteId; }
    public void setSiteId(String siteId) { this.siteId = siteId; }
    public String getGatewayId() { return gatewayId; }
    public void setGatewayId(String gatewayId) { this.gatewayId = gatewayId; }
    public String getPlantId() { return plantId; }
    public void setPlantId(String plantId) { this.plantId = plantId; }
    public String getZoneId() { return zoneId; }
    public void setZoneId(String zoneId) { this.zoneId = zoneId; }
    public Instant getTs() { return ts; }
    public void setTs(Instant ts) { this.ts = ts; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public Double getScore() { return score; }
    public void setScore(Double score) { this.score = score; }
    public String getModelVersion() { return modelVersion; }
    public void setModelVersion(String modelVersion) { this.modelVersion = modelVersion; }
    public String getImageKey() { return imageKey; }
    public void setImageKey(String imageKey) { this.imageKey = imageKey; }
    public Integer getImageW() { return imageW; }
    public void setImageW(Integer imageW) { this.imageW = imageW; }
    public Integer getImageH() { return imageH; }
    public void setImageH(Integer imageH) { this.imageH = imageH; }
    public List<Detection> getDetections() { return detections; }
    public void setDetections(List<Detection> detections) { this.detections = detections; }
    public boolean isAcked() { return acked; }
    public void setAcked(boolean acked) { this.acked = acked; }
    public Instant getReceivedAt() { return receivedAt; }
    public void setReceivedAt(Instant receivedAt) { this.receivedAt = receivedAt; }
}
