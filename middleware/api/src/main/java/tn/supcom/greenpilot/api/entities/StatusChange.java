package tn.supcom.greenpilot.api.entities;

import jakarta.nosql.Column;
import jakarta.nosql.Embeddable;
import java.time.Instant;

/** One step of a command life cycle. */
@Embeddable(Embeddable.EmbeddableType.GROUPING)
public class StatusChange {
    @Column
    private CommandStatus status;
    @Column
    private String reason;
    @Column
    private Instant at;

    public static StatusChange of(CommandStatus status, String reason) {
        StatusChange s = new StatusChange();
        s.status = status;
        s.reason = reason;
        s.at = Instant.now();
        return s;
    }

    // ---- getters / setters (generated)
    public CommandStatus getStatus() { return status; }
    public void setStatus(CommandStatus status) { this.status = status; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public Instant getAt() { return at; }
    public void setAt(Instant at) { this.at = at; }
}
