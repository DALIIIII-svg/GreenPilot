package tn.supcom.greenpilot.api.entities;

import jakarta.nosql.Column;
import jakarta.nosql.Entity;
import tn.supcom.greenpilot.core.entities.NosqlEntity;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Routine YOLO observation of one plant (F05). id = msgId:plantId. */
@Entity(Inspection.COLLECTION)
public class Inspection extends NosqlEntity {
    public static final String COLLECTION = "inspections";

    @Column
    private String msgId;
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
    private String status;    // HEALTHY | DISEASE_SUSPECTED | INCONCLUSIVE
    @Column
    private List<Detection> detections = new ArrayList<>();
    @Column
    private Integer imageW;
    @Column
    private Integer imageH;
    @Column
    private String modelVersion;
    @Column
    private String imageKey;
    @Column
    private String quality;
    @Column
    private Instant receivedAt;

    // ---- getters / setters (generated)
    public String getMsgId() { return msgId; }
    public void setMsgId(String msgId) { this.msgId = msgId; }
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
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public List<Detection> getDetections() { return detections; }
    public void setDetections(List<Detection> detections) { this.detections = detections; }
    public Integer getImageW() { return imageW; }
    public void setImageW(Integer imageW) { this.imageW = imageW; }
    public Integer getImageH() { return imageH; }
    public void setImageH(Integer imageH) { this.imageH = imageH; }
    public String getModelVersion() { return modelVersion; }
    public void setModelVersion(String modelVersion) { this.modelVersion = modelVersion; }
    public String getImageKey() { return imageKey; }
    public void setImageKey(String imageKey) { this.imageKey = imageKey; }
    public String getQuality() { return quality; }
    public void setQuality(String quality) { this.quality = quality; }
    public Instant getReceivedAt() { return receivedAt; }
    public void setReceivedAt(Instant receivedAt) { this.receivedAt = receivedAt; }
}
