package tn.supcom.greenpilot.iam.entities;

import jakarta.nosql.Column;
import jakarta.nosql.Entity;
import tn.supcom.greenpilot.core.entities.NosqlEntity;

import java.time.Instant;

/** Refresh token, stored only as a SHA-256 hash (id). Rotated: each refresh token works once. */
@Entity(RefreshToken.COLLECTION)
public class RefreshToken extends NosqlEntity {
    public static final String COLLECTION = "refresh_tokens";

    @Column
    private String tenantId;
    @Column
    private String identityId;
    @Column
    private String scopes;
    @Column
    private Instant expiresAt;

    // ---- getters / setters (generated)
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getIdentityId() { return identityId; }
    public void setIdentityId(String identityId) { this.identityId = identityId; }
    public String getScopes() { return scopes; }
    public void setScopes(String scopes) { this.scopes = scopes; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
}
