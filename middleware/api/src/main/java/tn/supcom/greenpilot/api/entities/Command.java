package tn.supcom.greenpilot.api.entities;

import jakarta.nosql.Column;
import jakarta.nosql.Entity;
import tn.supcom.greenpilot.core.entities.NosqlEntity;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** A command sent to a gateway. id = commandId (UUIDv7). PENDING -> ACCEPTED -> COMPLETED (or REJECTED/FAILED/EXPIRED/SEND_FAILED). */
@Entity(Command.COLLECTION)
public class Command extends NosqlEntity {
    public static final String COLLECTION = "commands";

    @Column
    private CommandType type;
    @Column
    private String siteId;
    @Column
    private String gatewayId;
    @Column
    private CommandStatus status;
    @Column
    private Instant issuedAt;
    @Column
    private Instant expiresAt;
    @Column
    private Instant updatedAt;
    @Column
    private String requestedBy;
    @Column
    private Integer durationSec;
    @Column
    private String payload;   // exact JSON sent over MQTT
    @Column
    private List<StatusChange> history = new ArrayList<>();

    // ---- getters / setters (generated)
    public CommandType getType() { return type; }
    public void setType(CommandType type) { this.type = type; }
    public String getSiteId() { return siteId; }
    public void setSiteId(String siteId) { this.siteId = siteId; }
    public String getGatewayId() { return gatewayId; }
    public void setGatewayId(String gatewayId) { this.gatewayId = gatewayId; }
    public CommandStatus getStatus() { return status; }
    public void setStatus(CommandStatus status) { this.status = status; }
    public Instant getIssuedAt() { return issuedAt; }
    public void setIssuedAt(Instant issuedAt) { this.issuedAt = issuedAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public String getRequestedBy() { return requestedBy; }
    public void setRequestedBy(String requestedBy) { this.requestedBy = requestedBy; }
    public Integer getDurationSec() { return durationSec; }
    public void setDurationSec(Integer durationSec) { this.durationSec = durationSec; }
    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }
    public List<StatusChange> getHistory() { return history; }
    public void setHistory(List<StatusChange> history) { this.history = history; }
}
