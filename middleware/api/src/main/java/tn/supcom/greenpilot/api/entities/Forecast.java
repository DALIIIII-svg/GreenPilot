package tn.supcom.greenpilot.api.entities;

import jakarta.nosql.Column;
import jakarta.nosql.Entity;
import tn.supcom.greenpilot.core.entities.NosqlEntity;
import java.time.Instant;

/** Soil-moisture prediction made on the Pi (4.1). id = msgId:zone. */
@Entity(Forecast.COLLECTION)
public class Forecast extends NosqlEntity {
    public static final String COLLECTION = "forecasts";

    @Column
    private String msgId;
    @Column
    private String siteId;
    @Column
    private String gatewayId;
    @Column
    private String zoneId;
    @Column
    private Instant ts;
    @Column
    private Integer horizonMin;
    @Column
    private Double predicted;
    @Column
    private Double baseline;
    @Column
    private String modelVersion;
    @Column
    private Instant receivedAt;

    // ---- getters / setters (generated)
    public String getMsgId() { return msgId; }
    public void setMsgId(String msgId) { this.msgId = msgId; }
    public String getSiteId() { return siteId; }
    public void setSiteId(String siteId) { this.siteId = siteId; }
    public String getGatewayId() { return gatewayId; }
    public void setGatewayId(String gatewayId) { this.gatewayId = gatewayId; }
    public String getZoneId() { return zoneId; }
    public void setZoneId(String zoneId) { this.zoneId = zoneId; }
    public Instant getTs() { return ts; }
    public void setTs(Instant ts) { this.ts = ts; }
    public Integer getHorizonMin() { return horizonMin; }
    public void setHorizonMin(Integer horizonMin) { this.horizonMin = horizonMin; }
    public Double getPredicted() { return predicted; }
    public void setPredicted(Double predicted) { this.predicted = predicted; }
    public Double getBaseline() { return baseline; }
    public void setBaseline(Double baseline) { this.baseline = baseline; }
    public String getModelVersion() { return modelVersion; }
    public void setModelVersion(String modelVersion) { this.modelVersion = modelVersion; }
    public Instant getReceivedAt() { return receivedAt; }
    public void setReceivedAt(Instant receivedAt) { this.receivedAt = receivedAt; }
}
