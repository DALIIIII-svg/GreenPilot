package tn.supcom.greenpilot.api.entities;

import jakarta.nosql.Column;
import jakarta.nosql.Entity;
import tn.supcom.greenpilot.core.entities.NosqlEntity;
import java.time.Instant;

/** Control auditing: who did what, when. id = UUIDv7 (time-ordered). */
@Entity(AuditEntry.COLLECTION)
public class AuditEntry extends NosqlEntity {
    public static final String COLLECTION = "audit_log";

    @Column
    private Instant ts;
    @Column
    private String action;
    @Column
    private String subject;
    @Column
    private String username;
    @Column
    private String siteId;
    @Column
    private String details;

    // ---- getters / setters (generated)
    public Instant getTs() { return ts; }
    public void setTs(Instant ts) { this.ts = ts; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getSiteId() { return siteId; }
    public void setSiteId(String siteId) { this.siteId = siteId; }
    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }
}
