package tn.supcom.greenpilot.iam.entities;

import jakarta.nosql.Column;
import jakarta.nosql.Entity;
import tn.supcom.greenpilot.core.entities.NosqlEntity;

import java.time.Instant;

/** Which scopes an identity approved for a tenant (course skeleton: Grant). id = tenantId + ":" + identityId. */
@Entity(Grant.COLLECTION)
public class Grant extends NosqlEntity {
    public static final String COLLECTION = "grants";

    @Column
    private String tenantId;
    @Column
    private String identityId;
    @Column
    private String approvedScopes;
    @Column
    private Instant issuanceDateTime;

    // ---- getters / setters (generated)
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getIdentityId() { return identityId; }
    public void setIdentityId(String identityId) { this.identityId = identityId; }
    public String getApprovedScopes() { return approvedScopes; }
    public void setApprovedScopes(String approvedScopes) { this.approvedScopes = approvedScopes; }
    public Instant getIssuanceDateTime() { return issuanceDateTime; }
    public void setIssuanceDateTime(Instant issuanceDateTime) { this.issuanceDateTime = issuanceDateTime; }
}
