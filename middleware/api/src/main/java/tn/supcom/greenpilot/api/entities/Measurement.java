package tn.supcom.greenpilot.api.entities;

import jakarta.nosql.Column;
import jakarta.nosql.Entity;
import tn.supcom.greenpilot.core.entities.NosqlEntity;
import java.time.Instant;

/** One sensor reading. id = msgId:zone:type, so a re-sent buffer overwrites instead of duplicating (F03). */
@Entity(Measurement.COLLECTION)
public class Measurement extends NosqlEntity {
    public static final String COLLECTION = "measurements";

    @Column
    private String msgId;
    @Column
    private String siteId;
    @Column
    private String gatewayId;
    @Column
    private String deviceId;
    @Column
    private String zoneId;    // null = greenhouse level (air sensor, reservoir)
    @Column
    private String type;      // soil_moisture | air_temperature | air_humidity | reservoir
    @Column
    private Double value;
    @Column
    private String unit;
    @Column
    private String quality;
    @Column
    private Instant ts;
    @Column
    private Instant receivedAt;

    // ---- getters / setters (generated)
    public String getMsgId() { return msgId; }
    public void setMsgId(String msgId) { this.msgId = msgId; }
    public String getSiteId() { return siteId; }
    public void setSiteId(String siteId) { this.siteId = siteId; }
    public String getGatewayId() { return gatewayId; }
    public void setGatewayId(String gatewayId) { this.gatewayId = gatewayId; }
    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
    public String getZoneId() { return zoneId; }
    public void setZoneId(String zoneId) { this.zoneId = zoneId; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public Double getValue() { return value; }
    public void setValue(Double value) { this.value = value; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public String getQuality() { return quality; }
    public void setQuality(String quality) { this.quality = quality; }
    public Instant getTs() { return ts; }
    public void setTs(Instant ts) { this.ts = ts; }
    public Instant getReceivedAt() { return receivedAt; }
    public void setReceivedAt(Instant receivedAt) { this.receivedAt = receivedAt; }
}
