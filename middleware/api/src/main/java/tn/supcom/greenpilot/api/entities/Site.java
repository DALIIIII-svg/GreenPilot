package tn.supcom.greenpilot.api.entities;

import jakarta.nosql.Column;
import jakarta.nosql.Entity;
import tn.supcom.greenpilot.core.entities.NosqlEntity;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** A greenhouse: location, gateway, zones/plants and control parameters. id = readable slug (greenhouse-01). */
@Entity(Site.COLLECTION)
public class Site extends NosqlEntity {
    public static final String COLLECTION = "sites";

    @Column
    private String name;
    @Column
    private GeoPoint location;
    @Column
    private String address;
    @Column
    private String gatewayId;
    @Column
    private List<Zone> zones = new ArrayList<>();
    @Column
    private IrrigationLimits irrigation = new IrrigationLimits();
    @Column
    private Thresholds thresholds = new Thresholds();
    @Column
    private Map<String, String> activeModels = new HashMap<>();    // kind -> version confirmed by the Pi
    @Column
    private Map<String, String> previousModels = new HashMap<>();  // kept for rollback
    @Column
    private Instant createdAt;
    @Column
    private Instant updatedAt;

    public boolean hasPlant(String plantId) {
        return zones.stream().anyMatch(z -> z.getPlants().stream().anyMatch(p -> p.getId().equals(plantId)));
    }

    // ---- getters / setters (generated)
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public GeoPoint getLocation() { return location; }
    public void setLocation(GeoPoint location) { this.location = location; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getGatewayId() { return gatewayId; }
    public void setGatewayId(String gatewayId) { this.gatewayId = gatewayId; }
    public List<Zone> getZones() { return zones; }
    public void setZones(List<Zone> zones) { this.zones = zones; }
    public IrrigationLimits getIrrigation() { return irrigation; }
    public void setIrrigation(IrrigationLimits irrigation) { this.irrigation = irrigation; }
    public Thresholds getThresholds() { return thresholds; }
    public void setThresholds(Thresholds thresholds) { this.thresholds = thresholds; }
    public Map<String, String> getActiveModels() { return activeModels; }
    public void setActiveModels(Map<String, String> activeModels) { this.activeModels = activeModels; }
    public Map<String, String> getPreviousModels() { return previousModels; }
    public void setPreviousModels(Map<String, String> previousModels) { this.previousModels = previousModels; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
