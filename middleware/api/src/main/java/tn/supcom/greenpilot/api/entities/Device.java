package tn.supcom.greenpilot.api.entities;

import jakarta.nosql.Column;
import jakarta.nosql.Entity;
import tn.supcom.greenpilot.core.entities.NosqlEntity;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Device description (F07). The W3C WoT Thing Description is kept verbatim as JSON text. */
@Entity(Device.COLLECTION)
public class Device extends NosqlEntity {
    public static final String COLLECTION = "devices";

    @Column
    private String siteId;
    @Column
    private String kind;      // ESP32_NODE | CAMERA | GATEWAY
    @Column
    private String name;
    @Column
    private List<String> zoneIds = new ArrayList<>();
    @Column
    private String thingDescription;
    @Column
    private Instant updatedAt;

    // ---- getters / setters (generated)
    public String getSiteId() { return siteId; }
    public void setSiteId(String siteId) { this.siteId = siteId; }
    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public List<String> getZoneIds() { return zoneIds; }
    public void setZoneIds(List<String> zoneIds) { this.zoneIds = zoneIds; }
    public String getThingDescription() { return thingDescription; }
    public void setThingDescription(String thingDescription) { this.thingDescription = thingDescription; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
