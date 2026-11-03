package tn.supcom.greenpilot.api.entities;

import jakarta.nosql.Column;
import jakarta.nosql.Embeddable;


/** Local rule thresholds pushed to the gateway (F07). */
@Embeddable(Embeddable.EmbeddableType.GROUPING)
public class Thresholds {
    @Column
    private Double soilMoistureLowPct;
    @Column
    private Double soilMoistureHighPct;

    // ---- getters / setters (generated)
    public Double getSoilMoistureLowPct() { return soilMoistureLowPct; }
    public void setSoilMoistureLowPct(Double soilMoistureLowPct) { this.soilMoistureLowPct = soilMoistureLowPct; }
    public Double getSoilMoistureHighPct() { return soilMoistureHighPct; }
    public void setSoilMoistureHighPct(Double soilMoistureHighPct) { this.soilMoistureHighPct = soilMoistureHighPct; }
}
