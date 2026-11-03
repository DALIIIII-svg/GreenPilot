package tn.supcom.greenpilot.api.entities;

import jakarta.nosql.Column;
import jakarta.nosql.Embeddable;


/** Bounded irrigation parameters (F02). */
@Embeddable(Embeddable.EmbeddableType.GROUPING)
public class IrrigationLimits {
    @Column
    private Integer maxDurationSec = 30;
    @Column
    private Integer cooldownSec = 300;

    // ---- getters / setters (generated)
    public Integer getMaxDurationSec() { return maxDurationSec; }
    public void setMaxDurationSec(Integer maxDurationSec) { this.maxDurationSec = maxDurationSec; }
    public Integer getCooldownSec() { return cooldownSec; }
    public void setCooldownSec(Integer cooldownSec) { this.cooldownSec = cooldownSec; }
}
