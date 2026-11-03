package tn.supcom.greenpilot.api.entities;

import jakarta.nosql.Column;
import jakarta.nosql.Embeddable;
import java.time.Instant;

/** A deployment request of a model release to a site. */
@Embeddable(Embeddable.EmbeddableType.GROUPING)
public class Deployment {
    @Column
    private String siteId;
    @Column
    private String commandId;
    @Column
    private String by;
    @Column
    private Instant at;

    // ---- getters / setters (generated)
    public String getSiteId() { return siteId; }
    public void setSiteId(String siteId) { this.siteId = siteId; }
    public String getCommandId() { return commandId; }
    public void setCommandId(String commandId) { this.commandId = commandId; }
    public String getBy() { return by; }
    public void setBy(String by) { this.by = by; }
    public Instant getAt() { return at; }
    public void setAt(Instant at) { this.at = at; }
}
